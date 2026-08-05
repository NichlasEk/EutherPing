# JNI symbols contain this exact class and method name. Renaming either breaks
# the native bridge in minified release builds.
-keep class se.apothictech.eutherping.crypto.vodozemac.VodozemacNative { *; }
