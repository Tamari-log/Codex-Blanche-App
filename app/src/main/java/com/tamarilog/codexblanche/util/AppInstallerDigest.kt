package com.tamarilog.codexblanche.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@Suppress("DEPRECATION")
private fun x509FromFirstSignature(pm: PackageManager, pkg: String): X509Certificate? {
    return try {
        val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
        val sig = pi.signatures?.firstOrNull() ?: return null
        CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(sig.toByteArray())) as X509Certificate
    } catch (_: Exception) {
        null
    }
}

private fun x509FromApkSigners(pm: PackageManager, pkg: String): X509Certificate? {
    return try {
        val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = pi.signingInfo?.apkContentsSigners ?: return null
        if (signatures.isEmpty()) return null
        CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(signatures[0].toByteArray())) as X509Certificate
    } catch (_: Exception) {
        null
    }
}

/** いま端末に入っているビルドの署名 SHA-1（Google Cloud の Android OAuth 用・コロン区切り大文字） */
object AppInstallerDigest {
    fun sha1ColonUpper(context: Context): String? {
        val pkg = context.packageName
        val pm = context.packageManager
        val cert: X509Certificate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            x509FromApkSigners(pm, pkg) ?: return null
        } else {
            x509FromFirstSignature(pm, pkg) ?: return null
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-1").digest(cert.encoded)
            digest.joinToString(":") { b -> "%02X".format(b) }
        } catch (_: Exception) {
            null
        }
    }
}
