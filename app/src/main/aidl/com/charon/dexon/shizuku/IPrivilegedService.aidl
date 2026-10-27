package com.charon.dexon.shizuku;

interface IPrivilegedService {
    String executeShellCommand(String command) = 1;
    void destroy() = 16777114;
}
