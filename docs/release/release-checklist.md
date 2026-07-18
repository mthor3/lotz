# Public Release Checklist

## Assets prepared in this repository

- Android launcher icons: `composeApp/src/androidMain/res/mipmap-*/ic_launcher.png`
- iOS App Store icon: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`
- Source artwork: `assets/lotz-app-icon-source.png`
- Store listing: `docs/release/store-listing.md`
- Privacy policy: `docs/release/privacy-policy.md`

## Screenshots

Capture at least these three screen states from the finished app at each store's required device sizes:

1. Configuration screen, with the selected game and strategy controls visible.
2. A completed single-run result, showing the spend, winnings, net result, and timeline.
3. A completed Monte Carlo batch, showing aggregate outcomes.

Do not use a screen that implies users can buy tickets or improve their chance of winning. Review screenshots for realistic-but-clearly-simulated values and remove any accidental personal data.

## Store configuration still owned by the publisher

- Create the Google Play Console and Apple Developer/App Store Connect records.
- Set the unique production bundle IDs and configure signing certificates/keystores.
- Add the privacy-policy URL after the repository is public.
- Complete each store's content and age-rating questionnaires using the guidance in `store-listing.md`.
- Set a public support contact in the store listing; GitHub Issues is the support channel named in the policy.
- Test the signed release builds on physical devices before submission.
