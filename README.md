# An Android App and Widget for Comparing Storm Forecasts

This app and related code is free to use for anyone. This was a side project to learn Android, kotlin and the many libraries that came along with it.

There are many Weather apps, but this one focused on these features:

1. Widgets: This app supports a gallery like widget for current storms
2. Unique Weather models. The weather models are scraped from various first party sources. Originally I was most interested in the relatively new HAFS-A and HAFS-B models that came out June 2023.

## Live in Production

The app can be downloaded from Google Play:
<https://play.google.com/store/apps/details?id=com.thirdgate.stormtracker>

## Setup

1. Clone repo and open with Android Studio.  Ensure gradle and libraries build properly.
2. App is built to use an API which scrapes and preprocesses storm data. The code for that can be found: <https://github.com/ddxv/stormtracker-api>
