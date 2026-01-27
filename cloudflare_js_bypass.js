/**
 * JavaScript-based Cloudflare Bypass Solution
 * This demonstrates how JavaScript execution can be used to solve Cloudflare challenges
 */

const axios = require('axios');
const cheerio = require('cheerio');
const vm = require('vm');

class CloudflareBypass {
    constructor() {
        this.session = axios.create({
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.9',
                'Accept-Encoding': 'gzip, deflate, br',
                'Connection': 'keep-alive',
                'Upgrade-Insecure-Requests': '1',
                'Sec-Fetch-Dest': 'document',
                'Sec-Fetch-Mode': 'navigate',
                'Sec-Fetch-Site': 'none',
                'Cache-Control': 'max-age=0'
            },
            timeout: 30000
        });
    }

    async bypass(url) {
        console.log(`Attempting to bypass Cloudflare for: ${url}`);
        
        try {
            // Initial request
            let response = await this.session.get(url);
            
            // Check if Cloudflare protection is present
            if (this.isCloudflareProtected(response)) {
                console.log('Cloudflare protection detected, attempting to solve...');
                
                // Parse the challenge
                const challengeData = this.parseChallenge(response.data);
                
                if (challengeData) {
                    // Solve the JavaScript challenge
                    const solvedResponse = await this.solveChallenge(url, challengeData);
                    
                    // Get the final content
                    response = await this.session.get(url, {
                        headers: {
                            'Referer': url,
                            'Cookie': solvedResponse.headers['set-cookie'] ? solvedResponse.headers['set-cookie'].join('; ') : ''
                        }
                    });
                }
            }
            
            return response.data;
        } catch (error) {
            console.error('Error bypassing Cloudflare:', error.message);
            throw error;
        }
    }

    isCloudflareProtected(response) {
        return response.status === 503 || 
               response.headers['server'] === 'cloudflare' ||
               response.data.includes('Checking your browser before accessing') ||
               response.data.includes('cf-browser-verification') ||
               response.data.includes('jschl-answer');
    }

    parseChallenge(html) {
        const $ = cheerio.load(html);
        
        // Look for Cloudflare form
        const form = $('form[class="challenge-form"]');
        if (!form.length) return null;
        
        // Extract challenge parameters
        const challenge = {
            s: $('input[name="s"]').val(),
            jschl_vc: $('input[name="jschl_vc"]').val(),
            pass: $('input[name="pass"]').val(),
            r: $('input[name="r"]').val(),
            host: $('input[name="host"]').val()
        };
        
        // Extract JavaScript challenge
        const scripts = $('script');
        let jsChallenge = '';
        
        scripts.each((i, elem) => {
            const scriptContent = $(elem).html();
            if (scriptContent.includes('setTimeout') && scriptContent.includes('jschl-answer')) {
                jsChallenge = scriptContent;
            }
        });
        
        challenge.js = jsChallenge;
        return challenge;
    }

    async solveChallenge(originalUrl, challenge) {
        return new Promise(async (resolve, reject) => {
            try {
                // Extract the mathematical expression from the JavaScript
                const match = challenge.js.match(/setTimeout\(function\(\)\{var.*?a\.value\s*=\s*([^;]+)/s);
                
                if (!match) {
                    reject(new Error('Could not extract JavaScript challenge'));
                    return;
                }
                
                let expression = match[1];
                
                // Clean up the expression to make it evaluable
                expression = expression.replace(/[\n\r\s]+/g, ' ')
                    .replace(/(a\.value)?\s*\+=\s*1\s*\+\s*s\.length/g, '+ 1 +' + challenge.r.length)
                    .replace(/t\s*=\s*document\[g\("cf-dn.*?\)\];/g, '')
                    .replace(/parseInt\(/g, 'Number.parseInt(');
                
                // Create a sandboxed context to evaluate the JavaScript safely
                const context = {
                    atob: (str) => Buffer.from(str, 'base64').toString('binary'),
                    document: {
                        getElementById: () => ({ offsetWidth: 0 })
                    },
                    location: {
                        hostname: new URL(originalUrl).hostname
                    }
                };
                
                // Execute the JavaScript in a safe context
                const result = vm.runInNewContext(expression, context, { timeout: 5000 });
                
                // Calculate the final answer (usually requires adding the domain length)
                const answer = Number(result) + new URL(originalUrl).hostname.length;
                
                // Submit the solved challenge
                setTimeout(async () => {
                    try {
                        const submitUrl = originalUrl.split('/cdn-cgi/l/chk_jschl')[0];
                        const response = await this.session.post(submitUrl + '/cdn-cgi/l/chk_jschl', {
                            s: challenge.s,
                            jschl_vc: challenge.jschl_vc,
                            pass: challenge.pass,
                            jschl_answer: answer
                        }, {
                            headers: {
                                'Referer': originalUrl,
                                'Content-Type': 'application/x-www-form-urlencoded'
                            }
                        });
                        
                        resolve(response);
                    } catch (error) {
                        reject(error);
                    }
                }, 4000); // Cloudflare typically requires waiting ~4 seconds
                
            } catch (error) {
                reject(error);
            }
        });
    }
}

// Usage example
async function example() {
    const bypasser = new CloudflareBypass();
    
    try {
        const content = await bypasser.bypass('https://blkom.com');
        console.log('Successfully bypassed Cloudflare!');
        console.log('Content length:', content.length);
    } catch (error) {
        console.error('Failed to bypass:', error.message);
    }
}

// Export for use in other modules
module.exports = CloudflareBypass;

// Run example if called directly
if (require.main === module) {
    example();
}