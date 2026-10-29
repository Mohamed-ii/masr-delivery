#!/bin/sh
cd "$(dirname "$0")" && java -cp out:testout masr.SelfTest
