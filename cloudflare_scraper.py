"""
Script to demonstrate how to scrape websites protected by Cloudflare
This uses Playwright which can handle JavaScript rendering and Cloudflare challenges
"""

from playwright.sync_api import sync_playwright
import time

def scrape_animeblkom():
    """
    Scrape animeblkom.net using Playwright to handle Cloudflare protection
    """
    with sync_playwright() as p:
        # Launch browser with additional options to appear more human-like
        browser = p.chromium.launch(
            headless=True,  # Set to False to see browser
            args=[
                '--no-sandbox',
                '--disable-blink-features=AutomationControlled',
                '--disable-dev-shm-usage'
            ]
        )
        
        # Create context with additional stealth options
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            viewport={'width': 1920, 'height': 1080},
            java_script_enabled=True,
            # Add extra headers to appear more human-like
            extra_http_headers={
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                "Accept-Language": "en-US,en;q=0.5",
                "Accept-Encoding": "gzip, deflate",
                "Connection": "keep-alive",
                "Upgrade-Insecure-Requests": "1",
            }
        )
        
        page = context.new_page()
        
        print("Navigating to animeblkom.net...")
        try:
            # Navigate to the site with a longer timeout
            page.goto("https://animeblkom.net/", wait_until="domcontentloaded", timeout=30000)
            
            # Wait for possible Cloudflare challenge to resolve
            print("Waiting for page to load and Cloudflare to resolve...")
            page.wait_for_timeout(10000)  # Wait 10 seconds to allow Cloudflare to resolve
            
            # Check if we got past Cloudflare by looking for specific elements
            title = page.title()
            print(f"Page title: {title}")
            
            # Check for Cloudflare-specific elements that would indicate we're still blocked
            cloudflare_indicators = [
                "Checking your browser before accessing",
                "Enable JavaScript and cookies to continue",
                "Just a moment...",
                "DDoS protection by Cloudflare"
            ]
            
            content = page.content()
            is_blocked = any(indicator in content for indicator in cloudflare_indicators)
            
            if is_blocked:
                print("Still blocked by Cloudflare. Site may require additional verification.")
            else:
                print("Appears to have passed Cloudflare protection!")
                
                # Save the content to a file
                with open("/workspace/animeblkom_content.html", "w", encoding="utf-8") as f:
                    f.write(content)
                    
                print("Content saved to animeblkom_content.html")
                
                # Take a screenshot to verify we passed Cloudflare
                page.screenshot(path="/workspace/animeblkom_screenshot.png")
                print("Screenshot saved to animeblkom_screenshot.png")
            
        except Exception as e:
            print(f"Error occurred: {e}")
            # Still save whatever content we might have gotten
            try:
                content = page.content()
                with open("/workspace/animeblkom_content.html", "w", encoding="utf-8") as f:
                    f.write(content)
                print("Partial content saved to animeblkom_content.html")
            except:
                pass
        
        finally:
            context.close()
            browser.close()

if __name__ == "__main__":
    scrape_animeblkom()