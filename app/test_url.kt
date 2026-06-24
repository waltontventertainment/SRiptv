import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

fun main() {
    try {
        var currentUrl = "https://is.gd/yQuS1g.m3u"
        var connection: HttpURLConnection? = null
        var redirectCount = 0
        
        while (redirectCount < 5) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.instanceFollowRedirects = false
            
            val code = connection.responseCode
            if (code in 300..399) {
                currentUrl = connection.getHeaderField("Location")
                println("Redirecting to: $currentUrl")
                redirectCount++
            } else {
                break
            }
        }
        
        println("Response Code: ${connection?.responseCode}")
        println("Content Type: ${connection?.contentType}")
        
        val reader = BufferedReader(InputStreamReader(connection?.inputStream))
        var count = 0
        while (true) {
            val line = reader.readLine() ?: break
            println(line)
            count++
            if (count > 10) break
        }
        reader.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
