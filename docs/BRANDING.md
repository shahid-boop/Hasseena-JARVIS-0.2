# Hasseena Branding

## Launcher icon
`app/src/main/res/drawable-nodpi/hasseena_icon.png`

The launcher icon uses the Hasseena neon-purple female AI visual with the H logo. It is referenced by both `android:icon` and `android:roundIcon`.

## Splash
`app/src/main/res/drawable-nodpi/hasseena_splash.png`

`SplashActivity` displays the branded Hasseena startup artwork for about 1.6 seconds and then opens the main assistant screen.

Android 12+ also gets a native system splash treatment through `values-v31/styles.xml`.

## Alternate loading artwork
`hasseena_loading.png` is included as an additional asset for a later animated/loading state.
