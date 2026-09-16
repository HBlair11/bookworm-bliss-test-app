from pathlib import Path
import re
import sys

FILE = Path("gradle.properties")


def main():
    if not FILE.exists():
        print("ERROR: gradle.properties was not found.")
        sys.exit(1)

    text = FILE.read_text(encoding="utf-8")
    code = re.search(r"^BOOKWORMBLISS_VERSION_CODE=(\d+)$", text, re.MULTILINE)
    name = re.search(r"^BOOKWORMBLISS_VERSION_NAME=(\d+)\.(\d+)$", text, re.MULTILINE)

    if not code or not name:
        print("ERROR: Bookworm Bliss version properties were not found.")
        sys.exit(1)

    old_code = int(code.group(1))
    old_major = int(name.group(1))
    old_minor = int(name.group(2))
    new_code = old_code + 1
    new_name = f"{old_major}.{old_minor + 1}"

    text = re.sub(r"^BOOKWORMBLISS_VERSION_CODE=\d+$",
                  f"BOOKWORMBLISS_VERSION_CODE={new_code}", text, count=1, flags=re.MULTILINE)
    text = re.sub(r"^BOOKWORMBLISS_VERSION_NAME=.*$",
                  f"BOOKWORMBLISS_VERSION_NAME={new_name}", text, count=1, flags=re.MULTILINE)
    FILE.write_text(text, encoding="utf-8")

    print(f"versionCode: {old_code} -> {new_code}")
    print(f"versionName: {old_major}.{old_minor} -> {new_name}")


if __name__ == "__main__":
    main()
