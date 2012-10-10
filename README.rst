Building
========

Install the Java and the `Android SDK`_.


Update the build files (optional):

::

    android update project -p . -n "Globus Transfer" -t "android-15"

Create a release key for yourself:

::

    keytool -genkey -v -keystore /path/to/private/keystore \
            -alias android -keyalg RSA -keysize 2048 -validity 10000

Create ant.properties pointing at your keystore:

::

    key.store=/path/to/private/keystore
    key.alias=android
    key.store.password=(your store password)
    key.alias.password=(your android key password)

Build a release:

::

    ant release

This will create an apk in ``bin/Globus Transfer-release.apk``. Note that if
you have already installed a release signed with someone else's key (e.g. the
apk linked from the blog post), you will need to uninstall that version first,
it will not allow you to upgrade if the signer doesn't match.

.. _Android SDK: http://developer.android.com/sdk/index.html
