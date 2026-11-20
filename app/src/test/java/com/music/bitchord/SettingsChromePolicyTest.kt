package com.music.bitchord
import com.music.bitchord.ui.navigation.*
import com.music.bitchord.ui.screens.*
import org.junit.Test
import org.junit.Assert.*
class SettingsChromePolicyTest {
 @Test fun settingsExceptionIncludesAllCategoriesAndChoices() {
  assertTrue(AppRoute.Settings.keepsSettingsChrome())
  SettingsCategory.entries.forEach { assertTrue(AppRoute.SettingsSection(it).keepsSettingsChrome()) }
  SettingsChoiceKind.entries.forEach { assertTrue(AppRoute.SettingsChoice(it).keepsSettingsChrome()) }
 }
 @Test fun directSettingsUtilitiesDoNotDependOnNavigationHistory() {
  listOf(AppRoute.Profile,AppRoute.SwitchProfile,AppRoute.ManageAccounts,AppRoute.AccountScrobbling,
   AppRoute.Sources,AppRoute.ListenTogether,AppRoute.WearOs,AppRoute.Equalizer,AppRoute.EqualizerPresets,
   AppRoute.AppUpdates,AppRoute.AboutDeveloper,AppRoute.CreditsLicenses,AppRoute.LicenseNotice("fixture"),
   AppRoute.Discord,AppRoute.Spotify).forEach { assertTrue(it.keepsSettingsChrome()) }
  listOf(AppRoute.History,AppRoute.Downloads,AppRoute.Moods,AppRoute.Replay).forEach { assertFalse(it.keepsSettingsChrome()) }
 }
}
