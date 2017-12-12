#!/bin/sh

#Based on https://superuser.com/questions/388956/generate-untrusted-server-p12-certificate-with-one-click

subject="/O=Conseil/OU=Fake Certs/CN=cryptonomic.tech"
file="conseil"

openssl req -new -newkey rsa:2048 -days 365 -x509 -out "$file.pem" -keyout "$file.key" -nodes

openssl pkcs12 -export -in "$file.pem" -inkey "$file.key" -out "$file.p12"
