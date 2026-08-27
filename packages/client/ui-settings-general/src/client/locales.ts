/** Shell chrome and General-nav dictionaries; feature rows own their copy. */

/** Simplified Chinese dictionary (the key-set source of truth). */
export const zh = {
  'trigger': '设置',
  'title': '设置',
  'close': '关闭',
  'openDocument': '打开配置文件',
  'openDocument.error': '无法打开配置文件',
  'general.nav': '通用设置',
  'android.title': 'Android 应用',
  'android.download': '下载 APK',
  'android.hint': '从当前 dsh web 主机下载 Android 客户端安装包（/dsh-android.apk）。',
} satisfies Record<string, string>

/** The settings namespace key union. */
export type SettingsKey = keyof typeof zh

/** English dictionary, checked complete against the zh key set. */
export const en = {
  'trigger': 'Settings',
  'title': 'Settings',
  'close': 'Close',
  'openDocument': 'Open configuration file',
  'openDocument.error': 'Could not open configuration file',
  'general.nav': 'General',
  'android.title': 'Android app',
  'android.download': 'Download APK',
  'android.hint': 'Download the Android client package (dsh-android.apk) from this dsh web host.',
} satisfies Record<SettingsKey, string>
