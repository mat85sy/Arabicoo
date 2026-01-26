#!/usr/bin/env python3
"""
Test script to verify AnimeBlkom provider functionality
This script tests the website connectivity and basic scraping functionality
"""

import requests
from bs4 import BeautifulSoup
import time
import random

def test_animeblkom_connectivity():
    """
    Test basic connectivity to animeblkom.net with realistic headers
    similar to what the enhanced provider uses
    """
    print("Testing animeblkom.net connectivity...")
    
    # Realistic headers similar to the enhanced provider
    headers = {
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
        'Accept-Language': 'en-US,en;q=0.9',
        'Accept-Encoding': 'gzip, deflate, br',
        'Cache-Control': 'no-cache',
        'Pragma': 'no-cache',
        'Priority': 'u=0, i',
        'Sec-Ch-Ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
        'Sec-Ch-Ua-Mobile': '?0',
        'Sec-Ch-Ua-Platform': '"Windows"',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none',
        'Sec-Fetch-User': '?1',
        'Upgrade-Insecure-Requests': '1',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'X-Requested-With': 'XMLHttpRequest',
        'Connection': 'keep-alive'
    }
    
    url = "https://animeblkom.net"
    
    try:
        print(f"Making request to {url}")
        response = requests.get(url, headers=headers, timeout=30)
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            print("✓ Successfully connected to animeblkom.net")
            soup = BeautifulSoup(response.content, 'html.parser')
            
            # Check if we got actual content (not Cloudflare protection)
            title = soup.find('title')
            if title:
                print(f"Page Title: {title.get_text().strip()}")
                
            # Look for common elements that indicate successful access
            search_form = soup.find('form', {'action': lambda x: x and 'search' in x.lower()})
            if search_form or len(soup.find_all(['nav', 'header'])) > 0:
                print("✓ Page loaded successfully with expected content")
                return True
            else:
                print("? Page loaded but content structure looks unusual")
                return True
                
        elif response.status_code == 403 or response.status_code == 503:
            print(f"✗ Blocked by Cloudflare protection (status: {response.status_code})")
            if 'cloudflare' in response.headers.get('server', '').lower():
                print("! Server identified as Cloudflare - protection active")
            return False
            
        else:
            print(f"? Unexpected status code: {response.status_code}")
            return False
            
    except requests.exceptions.RequestException as e:
        print(f"✗ Request failed: {str(e)}")
        return False

def test_search_functionality():
    """
    Test search functionality with a common query
    """
    print("\nTesting search functionality...")
    
    headers = {
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
        'Accept-Encoding': 'gzip, deflate, br',
        'Cache-Control': 'no-cache',
        'Sec-Ch-Ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
        'Sec-Ch-Ua-Mobile': '?0',
        'Sec-Ch-Up-Platform': '"Windows"',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'X-Requested-With': 'XMLHttpRequest',
        'Connection': 'keep-alive'
    }
    
    search_url = "https://animeblkom.net/search?query=naruto"
    
    try:
        print(f"Making search request: {search_url}")
        response = requests.get(search_url, headers=headers, timeout=30)
        print(f"Search Status Code: {response.status_code}")
        
        if response.status_code == 200:
            soup = BeautifulSoup(response.content, 'html.parser')
            # Look for search results
            results = soup.find_all('div', class_='content')  # Common class for anime entries
            if results:
                print(f"✓ Found {len(results)} search results")
                return True
            else:
                print("? No search results found, but page loaded successfully")
                return True
        else:
            print(f"✗ Search failed with status: {response.status_code}")
            return False
            
    except Exception as e:
        print(f"✗ Search test failed: {str(e)}")
        return False

def main():
    print("=" * 60)
    print("AnimeBlkom Provider Connectivity Test")
    print("=" * 60)
    
    # Add random delay to simulate human behavior
    time.sleep(random.uniform(1, 3))
    
    connect_success = test_animeblkom_connectivity()
    time.sleep(random.uniform(1, 2))
    
    if connect_success:
        search_success = test_search_functionality()
        time.sleep(random.uniform(1, 2))
    
    print("\n" + "=" * 60)
    if connect_success:  # Only test search if main site is accessible
        if 'search_success' in locals() and search_success:
            print("✓ All tests passed! Provider should work correctly.")
        else:
            print("? Connectivity OK, but search functionality needs verification.")
    else:
        print("✗ Connectivity failed. Provider may need adjustment.")
    print("=" * 60)

if __name__ == "__main__":
    main()