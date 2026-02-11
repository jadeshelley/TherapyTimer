#!/usr/bin/env perl
# OpenBLAS c_check stub for Android arm64 + CLANG. Does not run the compiler;
# writes a fixed Makefile.conf and config.h so the build works in WSL (avoids
# "clang: error: no input files" from the real c_check). Use after cloning
# OpenBLAS by copying over OpenBLAS/c_check.
# Invoked as: perl ./c_check Makefile.conf config.h <CC> <flags...>

use strict;
use warnings;

my $makefile = shift(@ARGV) or die "Usage: $0 <makefile> <config> [ignored...]\n";
my $config   = shift(@ARGV) or die "Usage: $0 <makefile> <config> [ignored...]\n";
# Rest (@ARGV) = CC + flags - we do not run the compiler

# Fixed output for Android arm64-v8a with CLANG (same format as real c_check)
open(my $MAKEFILE, ">", $makefile) or die "Can't create $makefile: $!\n";
open(my $CONFFILE, ">", $config)    or die "Can't create $config: $!\n";

print $MAKEFILE "OSNAME=Android\n";
print $MAKEFILE "ARCH=arm64\n";
print $MAKEFILE "C_COMPILER=CLANG\n";
print $MAKEFILE "BINARY32=\n";
print $MAKEFILE "BINARY64=1\n";
print $MAKEFILE "CROSS=1\n";
print $MAKEFILE "CEXTRALIB= -lm\n";

print $CONFFILE "#define OS_ANDROID\t1\n";
print $CONFFILE "#define ARCH_ARM64\t1\n";
print $CONFFILE "#define C_CLANG\t1\n";
print $CONFFILE "#define __64BIT__\t1\n";
print $CONFFILE "#define PTHREAD_CREATE_FUNC pthread_create\n";

close($MAKEFILE);
close($CONFFILE);
exit 0;
