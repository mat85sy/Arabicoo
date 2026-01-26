const { JSDOM } = require('jsdom');
const fetch = require('node-fetch').default;

// Mock CloudStream3 environment for testing
global.window = {};
global.document = {};
global.fetch = fetch;

// Test configuration
const TEST_URLS = [
    'https://animeblkom.net',
    'https://animeblkom.net/anime-list',
    'https://animeblkom.net/search?q=test'
];

// Enhanced headers to mimic real browser
const ENHANCED_HEADERS = {
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
    'Accept-Encoding': 'gzip, deflate, br',
    'Accept-Language': 'en-US,en;q=0.9',
    'Cache-Control': 'no-cache',
    'Pragma': 'no-cache',
    'Sec-Ch-Ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
    'Sec-Ch-Ua-Mobile': '?0',
    'Sec-Ch-Ua-Platform': '"Windows"',
    'Sec-Fetch-Dest': 'document',
    'Sec-Fetch-Mode': 'navigate',
    'Sec-Fetch-Site': 'none',
    'Sec-Fetch-User': '?1',
    'Upgrade-Insecure-Requests': '1',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
};

class CloudflareBypassTester {
    constructor() {
        this.sessionCookies = {};
        this.testResults = [];
    }

    // Test basic connectivity and headers
    async testBasicConnectivity(url) {
        console.log(`Testing basic connectivity to: ${url}`);
        
        try {
            const response = await fetch(url, {
                method: 'GET',
                headers: ENHANCED_HEADERS,
                redirect: 'follow',
                timeout: 10000
            });

            console.log(`Status: ${response.status}`);
            console.log(`Headers:`, response.headers.raw());

            const body = await response.text();
            
            // Check for Cloudflare protection indicators
            const hasCloudflare = this.detectCloudflareProtection(body);
            
            return {
                url: url,
                status: response.status,
                headers: response.headers,
                hasCloudflare: hasCloudflare,
                success: response.status === 200 && !hasCloudflare,
                bodyLength: body.length
            };
        } catch (error) {
            console.error(`Error testing ${url}:`, error.message);
            return {
                url: url,
                success: false,
                error: error.message
            };
        }
    }

    // Test with retry mechanism
    async testWithRetry(url, maxRetries = 3) {
        console.log(`\nTesting with retry mechanism for: ${url}`);
        
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            console.log(`Attempt ${attempt}/${maxRetries}`);
            
            try {
                const response = await fetch(url, {
                    method: 'GET',
                    headers: this.getRotatedHeaders(attempt),
                    redirect: 'follow',
                    timeout: 15000
                });

                const body = await response.text();
                const hasCloudflare = this.detectCloudflareProtection(body);

                console.log(`Attempt ${attempt} - Status: ${response.status}, CF Protection: ${hasCloudflare}`);

                if (response.status === 200 && !hasCloudflare) {
                    return {
                        success: true,
                        attempt: attempt,
                        status: response.status,
                        bodyLength: body.length
                    };
                }

                // Wait before next attempt with exponential backoff
                if (attempt < maxRetries) {
                    const delay = Math.pow(2, attempt) * 1000;
                    console.log(`Waiting ${delay}ms before retry...`);
                    await new Promise(resolve => setTimeout(resolve, delay));
                }

            } catch (error) {
                console.error(`Attempt ${attempt} failed:`, error.message);
                if (attempt === maxRetries) {
                    return {
                        success: false,
                        error: error.message,
                        attempt: attempt
                    };
                }
            }
        }

        return { success: false, attempt: maxRetries };
    }

    // Get rotated headers for each attempt
    getRotatedHeaders(attempt) {
        const baseHeaders = { ...ENHANCED_HEADERS };
        
        // Rotate some headers for each attempt
        if (attempt === 2) {
            baseHeaders['User-Agent'] = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
            baseHeaders['Sec-Ch-Ua'] = '"Chromium";v="120", "Google Chrome";v="120", "Not_A Brand";v="8"';
        } else if (attempt === 3) {
            baseHeaders['User-Agent'] = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
            baseHeaders['Accept'] = '*/*';
        }

        return baseHeaders;
    }

    // Detect Cloudflare protection in response
    detectCloudflareProtection(body) {
        const cfIndicators = [
            'Checking your browser before accessing',
            'Enable JavaScript to continue',
            'cloudflare',
            'cf-browser-verification',
            'Ray ID',
            'src="/cdn-cgi/challenge-platform/',
            'window._cf_chl_opt'
        ];

        return cfIndicators.some(indicator => 
            body.toLowerCase().includes(indicator.toLowerCase())
        );
    }

    // Run comprehensive tests
    async runTests() {
        console.log('Starting AnimeBlkom Provider Cloudflare Bypass Tests...\n');
        
        for (const url of TEST_URLS) {
            console.log(`\n${'='.repeat(60)}`);
            console.log(`Testing URL: ${url}`);
            console.log(`${'='.repeat(60)}`);
            
            // Basic connectivity test
            const basicResult = await this.testBasicConnectivity(url);
            console.log(`Basic Test Result: ${basicResult.success ? 'PASS' : 'FAIL'}`);
            
            // Retry mechanism test
            const retryResult = await this.testWithRetry(url);
            console.log(`Retry Test Result: ${retryResult.success ? 'PASS' : 'FAIL'}`);
            
            this.testResults.push({
                url: url,
                basicTest: basicResult,
                retryTest: retryResult
            });
        }

        this.printSummary();
    }

    // Print test summary
    printSummary() {
        console.log('\n' + '='.repeat(60));
        console.log('TEST SUMMARY');
        console.log('='.repeat(60));
        
        let totalTests = this.testResults.length * 2; // basic + retry per URL
        let passedTests = 0;

        for (const result of this.testResults) {
            if (result.basicTest.success) passedTests++;
            if (result.retryTest.success) passedTests++;
            
            console.log(`\nURL: ${result.url}`);
            console.log(`  Basic Test: ${result.basicTest.success ? 'PASS' : 'FAIL'}`);
            console.log(`  Retry Test: ${result.retryTest.success ? 'PASS' : 'FAIL'}`);
        }

        const passRate = ((passedTests / totalTests) * 100).toFixed(2);
        console.log(`\nOverall Pass Rate: ${passRate}% (${passedTests}/${totalTests})`);

        if (passRate >= 75) {
            console.log('✅ IMPLEMENTATION IS WORKING WELL');
        } else if (passRate >= 50) {
            console.log('⚠️  PARTIAL SUCCESS - MAY NEED FURTHER IMPROVEMENTS');
        } else {
            console.log('❌ IMPLEMENTATION NEEDS SIGNIFICANT IMPROVEMENTS');
        }
    }
}

// Run the tests
async function main() {
    const tester = new CloudflareBypassTester();
    await tester.runTests();
}

// Export for module usage
module.exports = { CloudflareBypassTester };

// Run if this file is executed directly
if (require.main === module) {
    main().catch(console.error);
}