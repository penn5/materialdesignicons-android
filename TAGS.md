# Recognised tags
## ignore-string-android
Do not process this string on Android
## fastlane-android
Process this string as Fastlane app metadata on Android
## require-all-*\[-keep-<platform>]
Indicates that all tags in this group must be present for any of them to be written.
If the tag ends with `-keep-<platform>` (where platform is fastlane-android or android),
it will be written regardless of the group decision
(but if it's missing, others in the group won't be written).