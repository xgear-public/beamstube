This is a Kotlin Multiplatform project targeting Desktop.

This is a Kotlin Multiplatform project. It has implementation for desktop JVM application with distribution to macOS.
This application loads videos from specified YouTube Channels and conveniently presents them.
I've attached a file with the entire codebase of this KMP application.

Key features of the application include:

Topic-Based Organization: Users can create custom "topics" and assign multiple YouTube channels to each topic.
Video Fetching: The application fetches the latest videos from the specified channels, filtering out Shorts and older content.
AI-Powered Summarization: Users can get AI-generated summaries of video transcripts, with support for multiple languages.
Watched History: The application keeps track of watched videos for the last three days.
Customizable Interface: It includes features for reordering topics and managing channel lists within topics.
Cross-Platform UI: Built with Compose Multiplatform, the application provides a consistent user experience.
Desktop Integration: The application integrates with the desktop environment, offering a tray icon and native menu bar actions.
Multi-language support: The app interface and summarization results can be displayed in different languages.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.



Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…