package android.net

/**
 * Concrete implementation of Android Uri for pure JVM unit testing.
 * Resides in package `android.net` to legitimately access Uri's package-private constructor.
 */
class TestUri(private val raw: String) : Uri() {
    override fun toString(): String = raw
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = raw.substringBefore("://")
    override fun getSchemeSpecificPart(): String = raw.substringAfter("://")
    override fun getEncodedSchemeSpecificPart(): String = raw.substringAfter("://")
    override fun getAuthority(): String = raw.substringAfter("://").substringBefore("/")
    override fun getEncodedAuthority(): String = getAuthority()
    override fun getPath(): String = "/" + raw.substringAfter("://").substringAfter("/")
    override fun getEncodedPath(): String = getPath()
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = getPath().split("/").filter { it.isNotEmpty() }
    override fun getLastPathSegment(): String? = getPathSegments().lastOrNull()
    override fun buildUpon(): Builder? = null
    override fun compareTo(other: Uri?): Int = 0
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = getAuthority()
    override fun getPort(): Int = -1
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: android.os.Parcel, flags: Int) {}
}
