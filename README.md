# Media_Server - Android APK Build Guide

## কেন আগের .apk ফাইলে "There was a problem parsing the package" দেখাচ্ছিল?
ব্রাউজার থেকে সরাসরি তৈরি করা প্লেইন-টেক্সট জিপ ফাইলে কম্পাইল করা `classes.dex`, বাইনারি `AndroidManifest.xml`, এবং RSA ডিজিটাল সিগনেচার থাকে না—তাই অ্যান্ড্রয়েড ফোন তা ইনস্টল করতে পারে না।

এই প্যাকেজের ভেতর **সম্পূর্ণ ফিক্সড অ্যান্ড্রয়েড সোর্স কোড** রয়েছে যেখানে সব পারমিশন, ব্যাকগ্রাউন্ড প্লে এবং অ্যান্টি-জুম লক যুক্ত করা আছে:
- সক্রিয় পারমিশন: INTERNET, GPS Location, Camera, Microphone, Storage/FilePicker, Background Play (Foreground Service + WakeLock)
- জুম লক: সম্পূর্ণ বন্ধ (No Pinch / Double-Tap Zoom)
- লোডিং স্ক্রিন: IMAGE (3s)

## আসল ইনস্টলযোগ্য .apk তৈরি করার ২টি সহজ উপায়:
1. **উবুন্টু / লিনাক্স সার্ভারে ১ কমান্ডে:**
   `chmod +x build-apk.sh && ./build-apk.sh`
   তারপর `app/build/outputs/apk/debug/app-debug.apk` সরাসরি ফোনে ইনস্টল করুন!
2. **Android Studio-তে:**
   ফোল্ডারটি ওপেন করে **Build > Build Bundle(s) / APK(s) > Build APK(s)** ক্লিক করুন।
3. **GitHub-এ (কোনো সফটওয়্যার ইনস্টল ছাড়াই):**
   এই ফোল্ডারটি GitHub-এ আপলোড করলে `.github/workflows/build-apk.yml` স্বয়ংক্রিয়ভাবে আসল `.apk` বিল্ড করে দেবে!
