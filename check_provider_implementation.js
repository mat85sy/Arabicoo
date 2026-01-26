/**
 * Script to check if the AnimeBlkomProvider implementation has the enhanced Cloudflare bypass features
 */

const fs = require('fs');

// Read the current provider file
const providerPath = '/workspace/AnimeBlkomProvider/src/main/kotlin/com/animeblkom/AnimeBlkomProvider.kt';

if (!fs.existsSync(providerPath)) {
    console.log(`Provider file does not exist at ${providerPath}`);
    
    // List all files in the AnimeBlkomProvider directory to see what we have
    const srcDir = '/workspace/AnimeBlkomProvider/src';
    if (fs.existsSync(srcDir)) {
        console.log(`Contents of ${srcDir}:`);
        const items = fs.readdirSync(srcDir, { recursive: true });
        items.forEach(item => console.log(`  - ${item}`));
    }
    process.exit(1);
}

const providerCode = fs.readFileSync(providerPath, 'utf8');

console.log('Checking AnimeBlkomProvider implementation for Cloudflare bypass enhancements...\n');

// Check for various enhancement indicators
const checks = [
    {
        name: 'CloudflareKiller Import',
        pattern: /import.*CloudflareKiller/,
        found: false
    },
    {
        name: 'CloudflareKiller Usage',
        pattern: /addInterceptor\s*\(\s*new\s+CloudflareKiller/i,
        found: false
    },
    {
        name: 'Enhanced Headers',
        pattern: /headers\s*:\s*\{/i,
        found: false
    },
    {
        name: 'User-Agent Header',
        pattern: /User-Agent|user-agent|User-Agent/i,
        found: false
    },
    {
        name: 'Sec-* Headers',
        pattern: /Sec-Ch-Ua|Sec-Fetch/i,
        found: false
    },
    {
        name: 'Retry Logic',
        pattern: /retry|setTimeout|sleep|delay|backoff/i,
        found: false
    },
    {
        name: 'Async/Await Pattern',
        pattern: /async\s+\w+\s*\(.*\)\s*{|await\s+/i,
        found: false
    },
    {
        name: 'Error Handling',
        pattern: /try\s*{|\bcatch\b|\bfinally\b/i,
        found: false
    }
];

// Run all checks
checks.forEach(check => {
    check.found = check.pattern.test(providerCode);
});

// Display results
console.log('ENHANCEMENT CHECK RESULTS:');
console.log('='.repeat(50));

let passedChecks = 0;
checks.forEach(check => {
    const status = check.found ? '✅ FOUND' : '❌ MISSING';
    console.log(`${status} ${check.name}`);
    if (check.found) passedChecks++;
});

console.log('\n' + '='.repeat(50));
const passRate = (passedChecks / checks.length) * 100;
console.log(`Implementation Completeness: ${passRate.toFixed(1)}% (${passedChecks}/${checks.length})`);

if (passRate >= 70) {
    console.log('✅ PROVIDER HAS GOOD CLOUDFLARE BYPASS IMPLEMENTATION');
} else if (passRate >= 40) {
    console.log('⚠️  PROVIDER HAS SOME CLOUDFLARE BYPASS FEATURES BUT NEEDS IMPROVEMENTS');
} else {
    console.log('❌ PROVIDER LACKS ADEQUATE CLOUDFLARE BYPASS IMPLEMENTATION');
}

// Show specific parts of the code that relate to Cloudflare bypass
console.log('\nSPECIFIC CODE SECTIONS:');
console.log('='.repeat(50));

// Extract header-related code
const headerMatches = providerCode.match(/(headers\s*:\s*\{[\s\S]*?\})|("User-Agent"|Sec-Ch-Ua|Accept-Language)/gi);
if (headerMatches) {
    console.log('Found header-related code:');
    headerMatches.slice(0, 10).forEach((match, index) => {
        console.log(`  ${index + 1}. ${match.substring(0, 100)}${match.length > 100 ? '...' : ''}`);
    });
}

// Extract function definitions
const funcMatches = providerCode.match(/async\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\([^)]*\)\s*{/g);
if (funcMatches) {
    console.log(`\nFound ${funcMatches.length} async functions that might handle requests:`);
    funcMatches.forEach((match, index) => {
        console.log(`  ${index + 1}. ${match}`);
    });
}

// Extract any fetch/request related code
const requestMatches = providerCode.match(/(fetch|get|post|request|http)[^}]*?(\.then|await|Promise)[^{]*{[^}]*}/gi);
if (requestMatches) {
    console.log(`\nFound ${requestMatches.length} request-related code blocks:`);
    requestMatches.slice(0, 3).forEach((match, index) => {
        console.log(`  ${index + 1}. ${match.substring(0, 200)}...`);
    });
}

// Provide recommendations based on findings
console.log('\nRECOMMENDATIONS:');
console.log('='.repeat(50));

if (!checks.find(c => c.name === 'CloudflareKiller Usage').found) {
    console.log('• Add CloudflareKiller interceptor: addInterceptor(new CloudflareKiller())');
}

if (!checks.find(c => c.name === 'Enhanced Headers').found) {
    console.log('• Add realistic browser headers including User-Agent, Sec-* headers, etc.');
}

if (!checks.find(c => c.name === 'Retry Logic').found) {
    console.log('• Implement retry logic with exponential backoff for failed requests');
}

if (!checks.find(c => c.name === 'Error Handling').found) {
    console.log('• Add proper error handling for network requests');
}

console.log('\nThe current provider implementation needs to be updated with the enhanced Cloudflare bypass features.');