import re
import json

with open("drive_page.html", "r", encoding="utf-8") as f:
    content = f.read()

# Replace \x22 with " and \x5b with [ and \x5d with ]
clean_content = content.replace(r'\x22', '"').replace(r'\x5b', '[').replace(r'\x5d', ']').replace(r'\/', '/')

# Let's search for all items: [ "ID", ["PARENT_ID"], "NAME", "MIME_TYPE", ... ]
matches = re.findall(r'\["([a-zA-Z0-9_-]{25,})",\["([a-zA-Z0-9_-]{25,})"\],"([^"]+)",\s*"([^"]+)"', clean_content)
print(f"Found {len(matches)} direct item matches:")
for m in matches:
    print(f"ID: {m[0]} | Parent: {m[1]} | Name: {m[2]} | Type: {m[3]}")

# Also find subfolders
folder_matches = re.findall(r'\[\[null,"([a-zA-Z0-9_-]{25,})"\],[^,]*?,[^,]*?,[^,]*?,"application/vnd.google-apps.folder"', clean_content)
print("Folder IDs:", set(folder_matches))

# Also search for any occurrence of folder names or file names
names = re.findall(r'\["([a-zA-Z0-9_-]{25,})",\["([a-zA-Z0-9_-]{25,})"\],"([^"]+)"', clean_content)
print(f"Found {len(names)} name matches:")
for n in names:
    print(n)

