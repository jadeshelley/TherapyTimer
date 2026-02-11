#!/usr/bin/env perl
# Patches OpenBLAS v0.3.13 Perl c_check so the compiler is invoked with list-form
# (no shell), so wrapper scripts receive correct argv and clang gets -E ctest.c.
# Fixes: "clang: error: no input files" when building with wrapper in WSL.
# Usage: perl fix_c_check.pl <path-to-c_check>

use strict;
use warnings;

my $file = $ARGV[0] or die "Usage: $0 <path-to-c_check>\n";
open my $fh, "<", $file or die "Cannot read $file: $!\n";
local $/;
my $content = <$fh>;
close $fh;

# Use absolute path for ctest.c; if compiler is our wrapper (clang-arm64.sh), read real clang
# path from wrapper and call it with -target. Put -E and path right after $cc so no flag eats the path.
my $repl = <<'REPL';
require Cwd;
my $ctest_c = Cwd::getcwd() . '/ctest.c';
$ctest_c =~ s/\s+$//;
die "ctest.c not found at $ctest_c" unless -f $ctest_c;
my $cc = $compiler_name;
my @extra = ();
my $cc_path = $compiler_name; $cc_path =~ s/\s+.*//;
if ($cc_path =~ m/clang-arm64\.sh$/ && -r $cc_path) {
  if (open(my $w, '<', $cc_path)) {
    <$w>; my $exec_line = <$w>;
    close $w;
    if ($exec_line && $exec_line =~ m/exec\s+(\S+)/) { $cc = $1; @extra = ('-target', 'aarch64-linux-android21'); }
  }
}
my @cc_cmd = ($cc, '-E', $ctest_c, @extra, grep(length, split(/\s+/, $flags)));
open(my $cc_fh, "-|", @cc_cmd) or die "Cannot run compiler: $!";
$data = join('', <$cc_fh>);
close($cc_fh);
REPL

# Replace both backtick invocations (first at line ~29, second at ~187)
my $pattern = qr/\$data = `\$compiler_name \$flags -E ctest\.c`;/;
my $n = ($content =~ s/$pattern/$repl/g);
warn "Expected 2 replacements in $file, got $n\n" if $n != 2;
exit 1 if $n < 1;

open $fh, ">", $file or die "Cannot write $file: $!\n";
print $fh $content;
close $fh;
print "Patched $file: replaced $n backtick compiler invocations with list-form.\n";
