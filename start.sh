#!/bin/bash
export MONITOR_IP=43.142.85.176
export SELF_IP=43.142.85.176
nohup java -javaagent:mcp-sidecar-encrypted.jar="-pwd hxkj2022" \
-XX:+DisableAttachMechanism -Dspring.profiles.active=server -DmonitorIp=$MONITOR_IP \
-DmediaServerIp=$MEDIA_SERVER_IP -DstreamIp=$STREAM_IP
-DselfIp=$SELF_IP -jar mcp-sidecar-encrypted.jar >> /dev/null &