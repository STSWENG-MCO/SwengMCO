package com.enil.logez.feature.privacy

/** One block of policy text: a paragraph, or a bulleted list. */
sealed interface PolicyBlock {
    data class Paragraph(val text: String) : PolicyBlock
    data class Bullets(val items: List<String>) : PolicyBlock
}

data class PolicySection(val heading: String, val blocks: List<PolicyBlock>)

data class PrivacyPolicyDocument(
    val title: String,
    val effectiveDate: String,
    val sections: List<PolicySection>,
)

/**
 * The one source of LogEZ's privacy policy (Play-readiness audit, 2026-09-25). The in-app screen
 * renders it, and [PrivacyPolicyHtml] renders the same document as the page hosted for Google Play.
 * Health Connect requires the hosted policy to be the same one users see in the app, and a unit
 * test fails whenever the committed `site/privacy/index.html` stops matching this file, so the two
 * cannot drift.
 *
 * Replaced a single string resource that opened "LogEZ does not collect any data", which Play's
 * definition contradicts (map tile requests leave the device), and that named no developer,
 * contact, effective date, retention or deletion route.
 *
 * English only, deliberately not in strings.xml: a legal text must say the same thing in the app
 * and on the web, and the app ships in English only. Change [EFFECTIVE_DATE] whenever the meaning
 * changes, and describe the change in that release's notes.
 */
object PrivacyPolicyContent {
    const val DEVELOPER = "Enil (Linus William Tabanao)"
    const val EFFECTIVE_DATE = "26 September 2026"

    /** Shown wherever the contact belongs while `logez.contactEmail` is unset; release builds refuse to build then. */
    const val CONTACT_PLACEHOLDER = "[contact email not set: add logez.contactEmail to gradle.properties]"

    fun document(contactEmail: String): PrivacyPolicyDocument {
        val contact = contactEmail.ifBlank { CONTACT_PLACEHOLDER }
        return PrivacyPolicyDocument(
            title = "LogEZ Privacy Policy",
            effectiveDate = EFFECTIVE_DATE,
            sections = listOf(
                PolicySection(
                    "Who we are",
                    listOf(
                        PolicyBlock.Paragraph(
                            "LogEZ is a workout logging app made by $DEVELOPER, an independent developer in the Philippines. " +
                                "In this policy, \"we\" means the developer. Questions about this policy or your data: $contact.",
                        ),
                    ),
                ),
                PolicySection(
                    "The short version",
                    listOf(
                        PolicyBlock.Bullets(
                            listOf(
                                "LogEZ has no account, no server, no ads and no analytics. We never receive your data unless you email us.",
                                "Everything you log stays on your phone.",
                                "The only thing LogEZ sends over the internet is requests for map data, and only while a map is on screen.",
                                "You can delete any of your data in the app, or all of it at once.",
                            ),
                        ),
                    ),
                ),
                PolicySection(
                    "What LogEZ stores on your phone",
                    listOf(
                        PolicyBlock.Paragraph("LogEZ keeps the following in its private storage on your device, which other apps cannot read:"),
                        PolicyBlock.Bullets(
                            listOf(
                                "Your workouts, routines, sets, notes, goals and settings, including a maximum heart rate if you enter one.",
                                "Exercises you create, including any photo you attach to one.",
                                "Body measurements and progress photos.",
                                "The route, distance and pace of each walk or run you track with GPS.",
                                "If you connect Health Connect: daily step and calorie totals, and heart-rate readings taken during your workouts.",
                                "Map data for the areas you have viewed, kept so maps load faster.",
                            ),
                        ),
                        PolicyBlock.Paragraph("None of this is uploaded, synced or sent to us."),
                    ),
                ),
                PolicySection(
                    "Location",
                    listOf(
                        PolicyBlock.Paragraph(
                            "When you track a walk or run, LogEZ uses your precise location to measure its distance, pace and route. " +
                                "It keeps doing so while the screen is off or you are in another app, until you tap Finish or Cancel. " +
                                "Android shows that LogEZ is running the whole time: as an ongoing notification if you allow LogEZ's " +
                                "notifications, and otherwise in its list of active apps. LogEZ does not use your location at any other time.",
                        ),
                        PolicyBlock.Paragraph(
                            "The route is saved only on your phone. Your phone's own location service (Google Play services on most " +
                                "Android phones) supplies the position to LogEZ; how that service works is set by your device settings " +
                                "and Google's policies.",
                        ),
                    ),
                ),
                PolicySection(
                    "Maps, the only thing that uses the internet",
                    listOf(
                        PolicyBlock.Paragraph(
                            "LogEZ shows a map while you track a walk or run, on its summary, and when you open it later in History. " +
                                "To draw that map it downloads map data (map tiles, labels and icons) from OpenFreeMap, a free map " +
                                "service run by Hyperknot Software Kft. in Hungary, over an encrypted HTTPS connection.",
                        ),
                        PolicyBlock.Paragraph(
                            "Like any web request, each request reveals your phone's IP address, basic technical details such as the " +
                                "app and Android version, and the area of the map on screen. While you are tracking, that area is " +
                                "roughly where you are; when you open a past walk or run, it is where that route was. The requests " +
                                "carry no account, no identifier and none of your workout data.",
                        ),
                        PolicyBlock.Paragraph(
                            "OpenFreeMap says it does not store IP addresses in its normal logs, keeps them for up to 30 days only " +
                                "while investigating a security incident, and may deliver tiles through Cloudflare. Its privacy policy " +
                                "is at https://openfreemap.org/privacy/",
                        ),
                    ),
                ),
                PolicySection(
                    "Health Connect",
                    listOf(
                        PolicyBlock.Paragraph(
                            "Connecting Health Connect is optional. If you connect it, LogEZ reads only the types you allow: steps, " +
                                "calories burned and heart rate. It never writes anything to Health Connect.",
                        ),
                        PolicyBlock.Bullets(
                            listOf(
                                "Steps are shown on the Profile tab, the Workout tab (today and the last 7 days), the Statistics screen (the last 30 days) and the home-screen widget. Calories burned are shown on the Profile tab.",
                                "Heart rate is shown during workouts and walk/run tracking, and as a chart on the summary shown when you finish and on the workout in History. Heart rate your watch syncs after you save is added when you next open that summary or workout. LogEZ keeps those readings with the workout, and they are included in full backups.",
                                "LogEZ keeps a copy of daily step and calorie totals, and of the heart-rate readings for each workout, on your phone.",
                            ),
                        ),
                        PolicyBlock.Paragraph(
                            "Data from Health Connect is used only to show your own figures back to you. It is never used for " +
                                "advertising, never sold, never shared with anyone, and never leaves your phone except inside a backup " +
                                "file you create yourself, or when you move LogEZ's data to a new phone with Android's phone-to-phone " +
                                "transfer. LogEZ's use of Health Connect data follows the Health Connect Permissions policy, including its " +
                                "Limited Use requirements.",
                        ),
                        PolicyBlock.Paragraph(
                            "To stop sharing and delete LogEZ's copy, use Settings > Export & backup > Disconnect and delete Health " +
                                "Connect data. The data in Health Connect itself is not touched.",
                        ),
                    ),
                ),
                PolicySection(
                    "Camera and photos",
                    listOf(
                        PolicyBlock.Paragraph(
                            "LogEZ uses the camera only when you take a progress photo. A photo you attach to an exercise is copied " +
                                "from your gallery into LogEZ. Both stay in LogEZ's private storage and are never uploaded.",
                        ),
                    ),
                ),
                PolicySection(
                    "Notifications",
                    listOf(
                        PolicyBlock.Paragraph(
                            "Notifications show your workout timer, rest timer and walk/run progress. LogEZ never sends marketing notifications.",
                        ),
                    ),
                ),
                PolicySection(
                    "When data leaves your phone because you chose it",
                    listOf(
                        PolicyBlock.Bullets(
                            listOf(
                                "Sharing a workout summary image sends it to the app you pick in Android's share sheet.",
                                "Saving a summary image puts it in your phone's Pictures folder.",
                                "Exporting workouts or measurements creates a CSV file where you choose.",
                                "A full backup creates a .zip file where you choose. It contains everything in LogEZ, including photos, GPS routes and Health Connect readings.",
                                "Settings > Send feedback opens your email app with a message to us whose subject contains your LogEZ and Android versions. If you send it, we receive your email address and what you write, and keep it only as long as needed to reply.",
                            ),
                        ),
                        PolicyBlock.Paragraph(
                            "These files are not encrypted, so anyone who can open them can read them. What happens to a file after " +
                                "you save or share it is up to you and the app or service you give it to.",
                        ),
                    ),
                ),
                PolicySection(
                    "Device backup and transfer",
                    listOf(
                        PolicyBlock.Paragraph(
                            "Android's cloud backup is turned off for LogEZ. On Android 12 and newer, a direct phone-to-phone transfer " +
                                "(for example when setting up a new phone with a cable) can copy LogEZ's data to the new phone. On older " +
                                "Android versions, use Settings > Export & backup > Full backup to move your data.",
                        ),
                    ),
                ),
                PolicySection(
                    "What LogEZ does not do",
                    listOf(
                        PolicyBlock.Bullets(
                            listOf(
                                "No accounts or sign-in.",
                                "No advertising, and no use of the advertising ID.",
                                "No analytics, crash-reporting or tracking tools.",
                                "No selling or renting of your data. The only data another company receives is the map request described above.",
                            ),
                        ),
                    ),
                ),
                PolicySection(
                    "How long data is kept, and how to delete it",
                    listOf(
                        PolicyBlock.Paragraph("Your data stays on your phone until you delete it:"),
                        PolicyBlock.Bullets(
                            listOf(
                                "Deleting a workout also deletes its sets, route and heart-rate readings.",
                                "Deleting a measurement entry deletes it. Deleting a progress photo also deletes the photo file.",
                                "Deleting an exercise you created hides it and deletes its photo; it stays listed in past workouts that used it.",
                                "Settings > Export & backup > Disconnect and delete Health Connect data deletes LogEZ's copy of Health Connect data.",
                                "Settings > Export & backup > Delete all data erases everything in LogEZ, including cached map data. It does not disconnect Health Connect; use the option above for that.",
                                "Uninstalling LogEZ, or clearing its storage in Android's settings, also deletes everything, except files you exported, images saved to Pictures, and any copy moved to another phone.",
                            ),
                        ),
                        PolicyBlock.Paragraph(
                            "Apart from emails you send us, we hold no copy of your data, so there is nothing for us to delete on our " +
                                "side. You are still welcome to contact us with any question.",
                        ),
                    ),
                ),
                PolicySection(
                    "Security",
                    listOf(
                        PolicyBlock.Paragraph(
                            "Your data is kept in LogEZ's private app storage. Map requests use encrypted HTTPS, and LogEZ blocks " +
                                "unencrypted connections entirely.",
                        ),
                    ),
                ),
                PolicySection(
                    "Children",
                    listOf(
                        PolicyBlock.Paragraph("LogEZ is made for adults and is not directed at children."),
                    ),
                ),
                PolicySection(
                    "Health information",
                    listOf(
                        PolicyBlock.Paragraph(
                            "LogEZ is not a medical device and does not diagnose, treat or prevent any condition. Heart-rate zones, " +
                                "calories and other figures are general fitness estimates. Talk to a healthcare professional before " +
                                "starting a new exercise programme.",
                        ),
                    ),
                ),
                PolicySection(
                    "Changes to this policy",
                    listOf(
                        PolicyBlock.Paragraph(
                            "If this policy changes, the effective date above changes too, and the app update's release notes say what changed.",
                        ),
                    ),
                ),
                PolicySection(
                    "Contact",
                    listOf(
                        PolicyBlock.Paragraph("$DEVELOPER, $contact"),
                    ),
                ),
            ),
        )
    }
}
