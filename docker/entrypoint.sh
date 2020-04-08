#!/bin/sh



DBHOST=${DB_Host:=db}
DBUSER=${DB_User:=user}
DBPW=${DB_Password:=password}
DBDB=${DB_Database:=conseil}
DBPORT=${DB_Port:=5432}
XTZSCHEME=${XTZ_Scheme:=http}
XTZHOST=${XTZ_Host:=node}
XTZPREFIX=${XTZ_Prefix:=}
XTZPORT=${XTZ_Port:=8732}
XTZNET=${XTZ_Network:=mainnet}
APIPORT=${API_PORT:=80}
APIKEY=${API_KEY:=conseil}
JVM_XMX_LORRE=${JVM_Xmx_Lorre:=8g}
JVM_XMX_CONSEIL=${JVM_Xmx_Conseil:=4g}

CONFIG=${CONFIG:-none}

if [ $CONFIG = "none" ]; then

    cp template.conf conseil.conf
    CONFIG="/root/conseil.conf"

    echo $CONFIG

    sed -i "s/{{DBHOST}}/$DBHOST/g" conseil.conf
    sed -i "s/{{DBUSER}}/$DBUSER/g" conseil.conf
    sed -i "s/{{DBPW}}/$DBPW/g" conseil.conf
    sed -i "s/{{DBDB}}/$DBDB/g" conseil.conf
    sed -i "s/{{DBPORT}}/$DBPORT/g" conseil.conf
    sed -i "s/{{XTZSCHEME}}/$XTZSCHEME/g" conseil.conf
    sed -i "s/{{XTZHOST}}/$XTZHOST/g" conseil.conf
    sed -i "s/{{XTZPREFIX}}/$XTZPREFIX/g" conseil.conf
    sed -i "s/{{XTZPORT}}/$XTZPORT/g" conseil.conf
    sed -i "s/{{XTZNET}}/$XTZNET/g" conseil.conf
    sed -i "s/{{APIPORT}}/$APIPORT/g" conseil.conf
    sed -i "s/{{APIKEY}}/$APIKEY/g" conseil.conf

else
    echo "Using config file: $CONFIG"
fi

if [ $1 = "conseil-api" ]; then
  java -Xmx$JVM_XMX_CONSEIL -Dconfig.file=$CONFIG -cp /root/conseil-api.jar tech.cryptonomic.conseil.api.Conseil
fi

if [ $1 = "conseil-lorre" ]; then
  java -Xmx$JVM_XMX_LORRE -Dconfig.file=$CONFIG -cp /root/conseil-lorre.jar tech.cryptonomic.conseil.lorre.Lorre $XTZNET
fi
