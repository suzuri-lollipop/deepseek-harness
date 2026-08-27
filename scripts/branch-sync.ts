/**
 * Classify uncommitted working-tree changes into feature branches according to
 * `scripts/branch-rules.json`, commit each group on its branch, merge every
 * touched branch into the merge target (develop), and push the result to the
 * configured remote (fork).
 *
 * Usage: `pnpm run branch:sync` from the merge target branch (develop). The
 * tool refuses to run from another branch while the tree is dirty.
 *
 * The tool never deletes user work: on any failure it keeps the temporary
 * snapshot branch (`branch-sync/snapshot`) and reports where the work stands.
 * Files matching no rule follow `unmatchedPolicy` (default: commit them
 * straight to the merge target); files matching a `skip` pattern are left in
 * the working tree and reported. Rules are evaluated in array order — list a
 * branch before another when its notes may be referenced by that branch's.
 */

import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/** Name of the temporary snapshot branch holding the staged working tree. */
const SNAPSHOT_BRANCH = 'branch-sync/snapshot'

/** Chunk size for git path lists, kept small for Windows command-line limits. */
const PATH_CHUNK = 100

/** One rule entry as parsed from the JSON file, before pattern compilation. */
interface RawBranchRule {
  readonly branch?: unknown
  readonly scope?: unknown
  readonly patterns?: unknown
}

/** Branch rule: one feature branch plus the file patterns that belong to it. */
interface BranchRule {
  readonly branch: string
  readonly scope: string
  readonly patterns: readonly RegExp[]
}

/** Loaded contents of `scripts/branch-rules.json`, normalized to `/` paths. */
interface BranchRules {
  readonly remote: string
  readonly mergeTarget: string
  readonly unmatchedPolicy: 'develop' | 'skip' | 'fail'
  readonly skip: readonly RegExp[]
  readonly rules: readonly BranchRule[]
}

/** Outcome of classifying one changed file. */
type Classification =
  | { readonly kind: 'skip' }
  | { readonly kind: 'branch'; readonly rule: BranchRule }
  | { readonly kind: 'unmatched' }

/** Run git, capture stdout, and throw on a non-zero exit. */
function git(root: string, ...args: readonly string[]): string {
  const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' })
  if (result.status !== 0) {
    throw new Error(`git ${args.join(' ')} failed (${String(result.status)}):\n${result.stderr.trim()}`)
  }
  return result.stdout
}

/** Run a user-visible git command with inherited stdio; throw on non-zero exit. */
function gitInherit(root: string, ...args: readonly string[]): void {
  const result = spawnSync('git', args, { cwd: root, stdio: 'inherit' })
  if (result.error !== undefined) throw result.error
  if (result.status !== 0) {
    throw new Error(`git ${args.join(' ')} failed with exit code ${String(result.status ?? 'unknown')}`)
  }
}

/** Run a git command over a path list in bounded chunks. */
function gitInheritChunked(root: string, argsPrefix: readonly string[], paths: readonly string[]): void {
  for (let i = 0; i < paths.length; i += PATH_CHUNK) {
    gitInherit(root, ...argsPrefix, ...paths.slice(i, i + PATH_CHUNK))
  }
}

/** Whether a git ref exists locally. */
function refExists(root: string, ref: string): boolean {
  const result = spawnSync('git', ['show-ref', '--verify', '--quiet', ref], { cwd: root })
  return result.status === 0
}

/** Whether an in-progress merge or rebase exists. */
function gitOperationInProgress(root: string): boolean {
  const mergeHead = spawnSync('git', ['rev-parse', '-q', '--verify', 'MERGE_HEAD'], { cwd: root })
  return mergeHead.status === 0 || existsSync(resolve(root, '.git', 'rebase-merge')) || existsSync(resolve(root, '.git', 'rebase-apply'))
}

/** Tracked worktree changes that are not staged (empty string when none). */
function untrackedAndModifiedTracked(root: string): string {
  return git(root, 'status', '--porcelain').split('\n').filter(line => line.length > 0 && !line.startsWith('??')).join('\n')
}

/**
 * Compile one repository-relative glob. `**` crosses directory separators, a
 * bare `*` stays inside one, and every other character is literal.
 */
function compileGlob(pattern: string): RegExp {
  let out = ''
  for (let i = 0; i < pattern.length; i += 1) {
    const c = pattern.charAt(i)
    if (c === '*') {
      if (pattern.charAt(i + 1) === '*') {
        if (pattern.charAt(i + 2) === '/') {
          out += '(?:[^/]+/)*'
          i += 2
        } else {
          out += '.*'
          i += 1
        }
      } else {
        out += '[^/]*'
      }
    } else if (c === '/') {
      out += '/'
    } else {
      out += c.replace(/[.+?^${}()|[\]\\]/g, '\\$&')
    }
  }
  return new RegExp(`^${out}$`)
}

/** Load and normalize the rule file. */
function loadRules(root: string): BranchRules {
  const raw = JSON.parse(readFileSync(resolve(root, 'scripts', 'branch-rules.json'), 'utf8')) as {
    remote?: unknown
    mergeTarget?: unknown
    unmatchedPolicy?: unknown
    skip?: unknown
    rules?: unknown
  }
  const text = (value: unknown, field: string): string => {
    if (typeof value !== 'string' || value.length === 0) throw new Error(`branch-rules.json: ${field} must be a non-empty string`)
    return value
  }
  const policy = raw.unmatchedPolicy
  if (policy !== 'develop' && policy !== 'skip' && policy !== 'fail') {
    throw new Error("branch-rules.json: unmatchedPolicy must be 'develop', 'skip', or 'fail'")
  }
  const normalize = (value: string): string => value.replace(/\\/g, '/')
  const skipRaw = (Array.isArray(raw.skip) ? raw.skip : []).filter((entry): entry is string => typeof entry === 'string')
  if (skipRaw.length !== (Array.isArray(raw.skip) ? raw.skip.length : 0)) {
    throw new Error('branch-rules.json: skip entries must be strings')
  }
  const rulesRaw: unknown[] = Array.isArray(raw.rules) ? raw.rules : []
  const rules = rulesRaw.map((entry, index) => {
    const rule = entry as RawBranchRule
    const branch = text(rule.branch, `rules[${index}].branch`)
    const scope = text(rule.scope, `rules[${index}].scope`)
    if (!Array.isArray(rule.patterns)) {
      throw new Error(`branch-rules.json: rules[${index}].patterns must be an array of strings`)
    }
    const patterns = rule.patterns.filter((p): p is string => typeof p === 'string')
    if (patterns.length !== rule.patterns.length) {
      throw new Error(`branch-rules.json: rules[${index}].patterns must be an array of strings`)
    }
    return { branch, scope, patterns: patterns.map(p => compileGlob(normalize(p))) }
  })
  return {
    remote: text(raw.remote, 'remote'),
    mergeTarget: text(raw.mergeTarget, 'mergeTarget'),
    unmatchedPolicy: policy,
    skip: skipRaw.map(p => compileGlob(normalize(p))),
    rules,
  }
}

/** Classify one repository-relative path against the rules (first match wins). */
function classify(rules: BranchRules, path: string): Classification {
  if (rules.skip.some(pattern => pattern.test(path))) return { kind: 'skip' }
  for (const rule of rules.rules) {
    if (rule.patterns.some(pattern => pattern.test(path))) return { kind: 'branch', rule }
  }
  return { kind: 'unmatched' }
}

/**
 * Collect every changed file as normalized `/` paths: tracked modifications
 * from porcelain v2 plus every untracked file from `ls-files --others`.
 */
function collectChangedFiles(root: string): string[] {
  const files = new Set<string>()
  const porcelain = git(root, 'status', '--porcelain=v2', '-z').split('\0').filter(entry => entry.length > 0)
  for (const entry of porcelain) {
    // Entry types: `1` changed (path is the last field), `2` rename/copy
    // (a score precedes the last field, which carries `new\told`), `u`
    // unmerged (cannot occur: the tool refuses to start with a merge open).
    let pair: string | undefined
    if (entry.startsWith('1 ')) {
      pair = entry.split(' ').slice(8).join(' ')
    } else if (entry.startsWith('2 ')) {
      pair = entry.split(' ').slice(9).join(' ')
    } else {
      continue
    }
    for (const side of pair.split('\t')) {
      const path = side.replace(/\\/g, '/')
      if (path.length > 0) files.add(path)
    }
  }
  for (const entry of git(root, 'ls-files', '--others', '--exclude-standard', '-z').split('\0')) {
    const path = entry.replace(/\\/g, '/')
    if (path.length > 0) files.add(path)
  }
  return [...files]
}

/** One branch's file bucket plus the commit that landed it, when any. */
interface BranchOutcome {
  readonly rule: BranchRule
  readonly files: readonly string[]
  readonly commit: string | null
}

/** Commit message for one auto-sync commit. */
function syncMessage(scope: string, target: string, files: readonly string[]): string {
  const list = files.map(file => `- ${file}`).join('\n')
  return `chore(${scope}): sync ${String(files.length)} changed file(s) to ${target}\n\n${list}`
}

/**
 * Abort the run, leaving the work recoverable. `keepStaged` (snapshot stage):
 * the staged content is the only copy, so it is preserved in a stash instead
 * of being discarded. Otherwise snapshot-derived staged content is recoverable
 * from the snapshot branch, so the tree is reset to the merge target.
 */
function fail(root: string, mergeTarget: string, startSha: string, detail: string, report: string[], keepStaged: boolean): never {
  if (keepStaged) {
    spawnSync('git', ['reset'], { cwd: root })
    spawnSync('git', ['stash', 'push', '-u', '-m', 'branch-sync recovery'], { cwd: root })
    spawnSync('git', ['checkout', mergeTarget], { cwd: root })
    if (refExists(root, `refs/heads/${SNAPSHOT_BRANCH}`)) {
      spawnSync('git', ['branch', '-D', SNAPSHOT_BRANCH], { cwd: root })
    }
    report.push(`FAILED: ${detail}`)
    report.push('Work saved to stash ("branch-sync recovery"); restore it with: git stash pop')
    console.error(report.join('\n'))
    process.exit(1)
  }
  // Undo an in-progress merge, if one is open.
  if (spawnSync('git', ['rev-parse', '-q', '--verify', 'MERGE_HEAD'], { cwd: root }).status === 0) {
    spawnSync('git', ['merge', '--abort'], { cwd: root })
  }
  const current = git(root, 'rev-parse', '--abbrev-ref', 'HEAD').trim()
  if (current !== mergeTarget) {
    spawnSync('git', ['checkout', '-f', mergeTarget], { cwd: root })
    spawnSync('git', ['reset', '--hard', mergeTarget], { cwd: root })
  }
  const snapshot = refExists(root, `refs/heads/${SNAPSHOT_BRANCH}`)
    ? git(root, 'rev-parse', `refs/heads/${SNAPSHOT_BRANCH}`).trim()
    : '(none created)'
  report.push(`FAILED: ${detail}`)
  report.push(`${mergeTarget} started at ${startSha}.`)
  report.push(`Snapshot branch ${SNAPSHOT_BRANCH} kept at ${snapshot} — inspect it or delete it after recovering.`)
  console.error(report.join('\n'))
  process.exit(1)
}

/** Fold hook-generated tracked changes (e.g. third-party notices) into the snapshot. */
function absorbHookOutput(root: string, maxPasses: number): void {
  for (let pass = 0; pass < maxPasses; pass += 1) {
    const dirty = git(root, 'status', '--porcelain').split('\n').filter(line => line.length > 0 && !line.startsWith('??'))
    if (dirty.length === 0) return
    gitInherit(root, 'add', '-u')
    gitInherit(root, 'commit', '-m', 'branch-sync: hook output (generated files)')
  }
}

/**
 * The sync pipeline: snapshot the working tree, commit each bucket on its
 * branch, merge touched branches into the merge target, commit the shared
 * bucket, and push. Throws on any failure so the caller can recover.
 */
function runSync(root: string, rules: BranchRules, mergeTarget: string, report: string[]): void {
  // Bring remote refs up to date. Merging remote movement happens once the
  // working tree is snapshotted (or immediately, when the tree is clean).
  gitInherit(root, 'fetch', rules.remote)
  const remoteTargetRef = `refs/remotes/${rules.remote}/${mergeTarget}`
  const mergeRemoteTarget = (): void => {
    if (refExists(root, remoteTargetRef) && git(root, 'rev-list', '--count', `${mergeTarget}..${remoteTargetRef}`).trim() !== '0') {
      console.log(`branch-sync: local ${mergeTarget} is behind ${rules.remote}/${mergeTarget}; merging it in.`)
      gitInherit(root, 'merge', '--no-edit', remoteTargetRef)
    }
  }

  // Classify every changed file into buckets keyed by branch name.
  const buckets = new Map<string, string[]>()
  const developFiles: string[] = []
  const skipped: string[] = []
  const unmatched: string[] = []
  for (const path of collectChangedFiles(root)) {
    const classification = classify(rules, path)
    if (classification.kind === 'skip') {
      skipped.push(path)
    } else if (classification.kind === 'branch') {
      const bucket = buckets.get(classification.rule.branch) ?? []
      bucket.push(path)
      buckets.set(classification.rule.branch, bucket)
    } else if (rules.unmatchedPolicy === 'develop') {
      developFiles.push(path)
    } else if (rules.unmatchedPolicy === 'skip') {
      unmatched.push(path)
    }
  }
  if (rules.unmatchedPolicy === 'fail' && unmatched.length > 0) {
    throw new Error(`unmatched files with policy 'fail':\n${unmatched.map(f => `- ${f}`).join('\n')}`)
  }
  const total = developFiles.length + [...buckets.values()].reduce((sum, files) => sum + files.length, 0)
  if (total === 0) {
    // Tree is clean: remote movement merges without touching local work.
    mergeRemoteTarget()
    console.log('branch-sync: nothing to do.')
    if (skipped.length > 0) console.log(`branch-sync: skipped ${String(skipped.length)} file(s) per skip patterns (left in working tree).`)
    if (unmatched.length > 0) console.log(`branch-sync: left ${String(unmatched.length)} unmatched file(s) per policy 'skip'.`)
    return
  }
  if (skipped.length > 0) {
    console.log(`branch-sync: skipping ${String(skipped.length)} file(s) per skip patterns (left in working tree).`)
  }

  // Stage on the merge target first (a failed snapshot commit then leaves the
  // content staged and recoverable in place), then commit it on a temporary
  // snapshot branch so branch switches start from a clean tree.
  const allFiles = [...developFiles, ...Array.from(buckets.values()).flat()]
  gitInheritChunked(root, ['add', '-A', '--'], allFiles)
  gitInherit(root, 'checkout', '-b', SNAPSHOT_BRANCH)
  try {
    gitInherit(root, 'commit', '-m', 'branch-sync: temporary snapshot of uncommitted changes')
  } catch (error) {
    // The staged snapshot is the only copy; preserve it instead of resetting.
    fail(root, mergeTarget, git(root, 'rev-parse', mergeTarget), error instanceof Error ? error.message : String(error), report, true)
  }
  absorbHookOutput(root, 2)
  const snapshotSha = git(root, 'rev-parse', 'HEAD').trim()
  if (untrackedAndModifiedTracked(root).trim() !== '') {
    throw new Error('working tree still dirty after snapshot commit; inspect and re-run')
  }
  const snapshotFiles = new Set(git(root, 'ls-tree', '-r', '--name-only', snapshotSha).split('\n').filter(f => f.length > 0))

  // The work is safe in the snapshot; return to the merge target and fold in
  // any remote movement before restoring buckets.
  gitInherit(root, 'checkout', mergeTarget)
  mergeRemoteTarget()

  // Commit each rule's bucket on its branch, then merge the branch in.
  const outcomes: BranchOutcome[] = []
  for (const rule of rules.rules) {
    const files = buckets.get(rule.branch)
    if (files === undefined || files.length === 0) continue
    if (!refExists(root, `refs/heads/${rule.branch}`)) {
      const remoteRef = `refs/remotes/${rules.remote}/${rule.branch}`
      if (refExists(root, remoteRef)) {
        gitInherit(root, 'checkout', '-b', rule.branch, remoteRef)
      } else {
        gitInherit(root, 'checkout', '-b', rule.branch, mergeTarget)
      }
    } else {
      gitInherit(root, 'checkout', rule.branch)
    }

    // If the remote branch moved, fold it in before layering the new commit.
    const remoteRef = `refs/remotes/${rules.remote}/${rule.branch}`
    if (refExists(root, remoteRef) && git(root, 'rev-list', '--count', `${rule.branch}..${remoteRef}`).trim() !== '0') {
      console.log(`branch-sync: ${rule.branch} is behind ${rules.remote}/${rule.branch}; merging it in.`)
      gitInherit(root, 'merge', '--no-edit', remoteRef)
    }
    // Bring in whatever landed on the merge target since this branch forked.
    if (git(root, 'rev-list', '--count', `${rule.branch}..${mergeTarget}`).trim() !== '0') {
      console.log(`branch-sync: updating ${rule.branch} from ${mergeTarget}.`)
      gitInherit(root, 'merge', '--no-edit', mergeTarget)
    }

    const present = files.filter(file => snapshotFiles.has(file))
    const deleted = files.filter(file => !snapshotFiles.has(file))
    gitInheritChunked(root, ['checkout', snapshotSha, '--'], present)
    if (deleted.length > 0) gitInherit(root, 'rm', '-f', '--ignore-unmatch', '--', ...deleted)
    const staged = spawnSync('git', ['diff', '--cached', '--quiet'], { cwd: root }).status
    let commit: string | null = null
    if (staged !== 0) {
      gitInherit(root, 'commit', '-m', syncMessage(rule.scope, rule.branch, files))
      absorbHookOutput(root, 2)
      commit = git(root, 'rev-parse', 'HEAD').trim()
    } else {
      console.log(`branch-sync: ${rule.branch} already contains these ${String(files.length)} file(s); no new commit.`)
    }

    gitInherit(root, 'checkout', mergeTarget)
    if (commit !== null || git(root, 'rev-list', '--count', `${mergeTarget}..${rule.branch}`).trim() !== '0') {
      gitInherit(root, 'merge', '--no-ff', '-m', `Merge branch '${rule.branch}' into ${mergeTarget}`, rule.branch)
    }
    outcomes.push({ rule, files, commit })
  }

  // Commit the unmatched (shared) bucket straight onto the merge target.
  // The bucket's content lives in the snapshot commit, not in the working
  // tree (the branch checkouts restored develop's own tree), so restore it.
  let developCommit: string | null = null
  if (developFiles.length > 0) {
    const present = developFiles.filter(file => snapshotFiles.has(file))
    const deleted = developFiles.filter(file => !snapshotFiles.has(file))
    gitInheritChunked(root, ['checkout', snapshotSha, '--'], present)
    if (deleted.length > 0) gitInherit(root, 'rm', '-f', '--ignore-unmatch', '--', ...deleted)
    if (spawnSync('git', ['diff', '--cached', '--quiet'], { cwd: root }).status === 0) {
      console.log(`branch-sync: ${mergeTarget} already contains these ${String(developFiles.length)} file(s); no new commit.`)
    } else {
      gitInherit(root, 'commit', '-m', syncMessage('repo', mergeTarget, developFiles))
      absorbHookOutput(root, 2)
      developCommit = git(root, 'rev-parse', 'HEAD').trim()
    }
  }
  if (untrackedAndModifiedTracked(root).trim() !== '') {
    throw new Error('working tree still dirty after the shared commit; inspect and re-run')
  }

  // Push the merge target and every configured branch that is ahead of its
  // remote, so a run also catches up branches that moved without a bucket.
  const pushCommands: string[][] = []
  const aheadOfRemote = (local: string, remoteRef: string): boolean =>
    refExists(root, remoteRef)
      ? git(root, 'rev-list', '--count', `${remoteRef}..${local}`).trim() !== '0'
      : true
  if (aheadOfRemote(mergeTarget, remoteTargetRef)) {
    pushCommands.push(['push', rules.remote, mergeTarget])
  }
  for (const rule of rules.rules) {
    const remoteRef = `refs/remotes/${rules.remote}/${rule.branch}`
    if (refExists(root, `refs/heads/${rule.branch}`) && aheadOfRemote(rule.branch, remoteRef)) {
      pushCommands.push(['push', '-u', rules.remote, rule.branch])
    }
  }
  if (pushCommands.length > 0) {
    console.log('branch-sync: pushing.')
    for (const args of pushCommands) gitInherit(root, ...args)
  }

  gitInherit(root, 'branch', '-D', SNAPSHOT_BRANCH)
  for (const outcome of outcomes) {
    report.push(outcome.commit === null
      ? `${outcome.rule.branch}: ${String(outcome.files.length)} file(s) already present; no new commit`
      : `${outcome.rule.branch}: ${String(outcome.files.length)} file(s) committed as ${outcome.commit}`)
  }
  if (developFiles.length > 0) {
    report.push(developCommit === null
      ? `${mergeTarget}: ${String(developFiles.length)} shared file(s) already present; no new commit`
      : `${mergeTarget}: ${String(developFiles.length)} shared file(s) committed as ${developCommit}`)
  }
  if (pushCommands.length === 0) report.push('push: skipped (no new commits)')
  else report.push(`push: ${pushCommands.map(args => `git ${args.join(' ')}`).join(' && ')}`)
  console.log(`branch-sync: done.\n${report.join('\n')}`)
}

/** Main entry: validate the starting state, then run the pipeline. */
function main(): void {
  const root = resolve(import.meta.dirname, '..')
  const rules = loadRules(root)
  const report: string[] = []
  const mergeTarget = rules.mergeTarget
  const startSha = git(root, 'rev-parse', 'HEAD').trim()

  if (gitOperationInProgress(root)) {
    fail(root, mergeTarget, startSha, 'a merge or rebase is in progress; finish it first', report, false)
  }
  const head = git(root, 'rev-parse', '--abbrev-ref', 'HEAD').trim()
  if (head !== mergeTarget) {
    const dirty = git(root, 'status', '--porcelain').trim()
    if (dirty.length > 0) {
      console.error(`branch-sync: run from ${mergeTarget} (current branch ${head} has uncommitted changes).`)
      process.exit(1)
    }
    gitInherit(root, 'checkout', mergeTarget)
  }
  if (refExists(root, `refs/heads/${SNAPSHOT_BRANCH}`)) {
    fail(root, mergeTarget, startSha, `snapshot branch ${SNAPSHOT_BRANCH} already exists from an earlier failed run; inspect and delete it first`, report, false)
  }

  try {
    runSync(root, rules, mergeTarget, report)
  } catch (error) {
    fail(root, mergeTarget, startSha, error instanceof Error ? error.message : String(error), report, false)
  }
}

main()
