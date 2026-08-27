/**
 * Android client row of the General section: a download link to the APK the
 * dsh web host serves beside the dist (/dsh-android.apk), so a phone user
 * installs the native client from the GUI.
 */
import type { PropsLocale, PropsRuntime } from '@deepseek-ai/dsh-client-ui-slots'
import css from './AndroidDownloadRow.module.css'

/** Full component props: runtime share + locale seat. */
export type AndroidDownloadRowProps =
  PropsRuntime<'settings.general.item'> & PropsLocale<'settings'>

/**
 * Render the Android client row.
 * @param props - composed slot props.
 * @returns the row element tree.
 */
export function AndroidDownloadRow({ t }: AndroidDownloadRowProps) {
  return (
    <div className={css.group}>
      <div className={css.title}>{t('android.title')}</div>
      <a className={css.download} href="/dsh-android.apk" download>
        {t('android.download')}
      </a>
      <div className={css.hint}>{t('android.hint')}</div>
    </div>
  )
}
