# MaxMail
E-Mail project for BlackBerry OS 7

#### NOTE: Trying to make it work again but possibly it will work on your own server, no master servers thats the meaning, you have to create your own server and pay for domain and static ip or get free servers to launch your special mail servers, i wouldnt do free servers due to credintals that you have to put to server to actually access to your gmail is a bit risky, if you can do, grab old pc, hook up on home network, buy domain and pay static ip fee montly and have mail application private to you, in development right now

To make it work you have to install NodeJS for server, to compile you can install NetBeans IDE 7.3.1, Sun Java Wireless Toolkit 2.5.2 and Java JDK 8 Update 202. After installation, DO NOT FORGET TO EDIT .env.example FILE AS WRITTEN INSIDE OF IT TO RUN YOUR APPLICATION AND SERVER, for server to run:
   ```bash
   npm install && node server.js
   ```
After that you have to get your static IP for your device and put it on the Midlet.java
   ```bash
   private String serverUrl = "http://YOUR_SERVER_IP:3000";
   ```
Now compile the project, get the output and put it to /public folder on server, or you can transfer it to your device, i personally wanna test the server installation so i put the build output to /public to install it to BlackBerry phone, open your applications and run MaxMail, it will wanna connect to server that you provided, after that you good to go, its a simple mail application, dont expect too much, i made tons of optimizations just to make that application run with another application at same time, try more if you can, that devices are so limited, tested on BlackBerry Bold 9900, works out-of-the box for me, i will not test other BlackBerry or J2ME supported devices, dont request, do it yourself, out.

