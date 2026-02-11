/*
 * CLAPACK compiler launcher: exec NDK clang with -target aarch64-linux-android21, optional --sysroot,
 * and optional -resource-dir (so copied clang finds stddef.h etc).
 * Files next to this binary: clang-for-clapack.path, .sysroot, .resource-dir (one line each).
 */
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>
#include <sys/stat.h>

static int read_line_file(const char *argv0, const char *suffix, char *out, size_t size) {
  char path_file[PATH_MAX];
  const char *slash = strrchr(argv0, '/');
  size_t dir_len = slash ? (size_t)(slash - argv0) + 1 : 0;
  size_t suf_len = strlen(suffix);
  if (dir_len + suf_len >= sizeof(path_file))
    return -1;
  memcpy(path_file, argv0, dir_len);
  memcpy(path_file + dir_len, suffix, suf_len + 1);
  FILE *f = fopen(path_file, "r");
  if (!f) return -1;
  if (!fgets(out, (int)size, f)) { fclose(f); return -1; }
  fclose(f);
  out[size - 1] = '\0';
  char *eol = strchr(out, '\n');
  if (eol) *eol = '\0';
  return 0;
}

int main(int argc, char **argv) {
  char path_buf[PATH_MAX];
  char sysroot_buf[PATH_MAX];
  char resource_buf[PATH_MAX];
  const char *clang = getenv("CLAPACK_CLANG");
  if (!clang || !*clang) {
    if (argv[0] && read_line_file(argv[0], "clang-for-clapack.path", path_buf, sizeof(path_buf)) == 0)
      clang = path_buf;
  }
  if (!clang || !*clang) {
    fprintf(stderr, "clang-for-clapack: set CLAPACK_CLANG or create clang-for-clapack.path next to this binary\n");
    return 127;
  }
  int has_sysroot = (argv[0] && read_line_file(argv[0], "clang-for-clapack.sysroot", sysroot_buf, sizeof(sysroot_buf)) == 0 && sysroot_buf[0] != '\0');
  int has_resource = (argv[0] && read_line_file(argv[0], "clang-for-clapack.resource-dir", resource_buf, sizeof(resource_buf)) == 0 && resource_buf[0] != '\0');
  /* [clang, -target, triple, (--sysroot, path)?, (-resource-dir, path)?, argv[1], ...] */
  int extra = 3 + (has_sysroot ? 2 : 0) + (has_resource ? 2 : 0);
  char **new_argv = malloc((size_t)(argc + extra) * sizeof(char *));
  if (!new_argv) {
    fprintf(stderr, "clang-for-clapack: malloc failed\n");
    return 127;
  }
  new_argv[0] = (char *)clang;
  new_argv[1] = "-target";
  new_argv[2] = "aarch64-linux-android21";
  int n = 3;
  char *dup_sysroot = NULL;
  char *dup_resource = NULL;
  if (has_sysroot) {
    new_argv[n++] = "--sysroot";
    new_argv[n++] = (dup_sysroot = strdup(sysroot_buf));
  }
  if (has_resource) {
    new_argv[n++] = "-resource-dir";
    new_argv[n++] = (dup_resource = strdup(resource_buf));
  }
  for (int i = 1; i < argc; i++)
    new_argv[n++] = argv[i];
  new_argv[n] = NULL;
  {
    struct stat st;
    int exists = (stat(clang, &st) == 0);
    int is_reg = exists && S_ISREG(st.st_mode);
    int is_exe = exists && (st.st_mode & 0111);
    if (!exists || !is_reg || !is_exe) {
      fprintf(stderr, "clang-for-clapack: path='%s' exists=%d regular=%d executable=%d\n",
              clang, exists, is_reg, is_exe);
    }
  }
  execv(clang, new_argv);
  free(dup_sysroot);
  free(dup_resource);
  fprintf(stderr, "clang-for-clapack: execv failed for path='%s' ", clang);
  perror("(execv)");
  return 127;
}
