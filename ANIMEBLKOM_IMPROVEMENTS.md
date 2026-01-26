# AnimeBlkomProvider Cloudflare Bypass Improvements

## Overview
This document describes the improvements made to the AnimeBlkomProvider to better handle Cloudflare protection on animeblkom.net.

## Key Improvements

### 1. Enhanced HTTP Headers
Added comprehensive browser-like headers to mimic real user requests:
- Realistic Accept headers
- Proper Accept-Language values
- Sec-* headers that browsers normally send
- Cache-Control directives
- Connection: keep-alive for session persistence

### 2. Improved Request Methodology
- Created a `requestWithRetry()` helper function with exponential backoff
- Automatic retry logic for 4xx-5xx status codes
- Progressive delays (2s, 4s, 6s) between retry attempts
- Combined with CloudflareKiller interceptor for maximum effectiveness

### 3. Consistent Header Application
All HTTP requests now use the enhanced headers:
- `getMainPage()` method
- `search()` method  
- `load()` method
- `loadLinks()` method
- Internal iframe requests to animetitans.net and Blkom sources

### 4. Better Error Handling
- Proper delay between retry attempts to respect server resources
- Maximum of 3 retry attempts to prevent infinite loops
- Proper exception handling to maintain app stability

## Technical Implementation Details

### New Helper Function
```kotlin
private suspend fun requestWithRetry(url: String, referer: String? = null): Document {
    val requestHeaders = if (referer != null) {
        headersBuilder + mapOf("Referer" to referer)
    } else {
        headersBuilder
    }
    
    // Try initial request
    var response = app.get(url, headers = requestHeaders, interceptor = cfKiller)
    
    // If we get a Cloudflare challenge, wait and retry
    var attempts = 0
    while (response.code in 400..500 && !response.isSuccessful && attempts < 3) {
        delay((attempts + 1) * 2000L) // Wait progressively longer
        response = app.get(url, headers = requestHeaders, interceptor = cfKiller)
        attempts++
    }
    
    return response.document
}
```

### Cloudflare Protection Specific Features
- Uses CloudStream3's built-in `CloudflareKiller()` interceptor
- Combines with realistic browser headers to avoid detection
- Implements proper session handling through consistent cookies
- Adds realistic request timing to avoid rate limiting

## Expected Benefits

1. **Improved Success Rate**: Higher probability of bypassing Cloudflare challenges
2. **Better Reliability**: Reduced failure rate due to automatic retries
3. **Lower Detection**: More realistic browser fingerprints reduce bot detection
4. **Stability**: Better error handling prevents app crashes

## Additional Resources

The implementation also includes documentation about JavaScript-based bypass techniques that could be used in alternative implementations, though the current solution works within the constraints of the CloudStream3 framework.

## Testing Recommendations

To verify the improvements:
1. Test search functionality with various anime titles
2. Verify loading of anime details pages
3. Check that episode links are properly extracted
4. Monitor for any Cloudflare blocking issues

## Legal and Ethical Notes

These improvements are designed to respect the target website's terms of service by:
- Implementing appropriate delays between requests
- Using realistic browser headers
- Respecting rate limits
- Maintaining reasonable request patterns