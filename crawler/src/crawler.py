import os
import json
import redis
from git import Repo
from pathlib import Path

STREAM = "dcse_stream"

r = redis.Redis(host="127.0.0.1", port=6379, decode_responses=True)

def clone_repo(repo_url, dest="cloned_repo"):
    if not os.path.exists(dest):
        print("Cloning repo...")
        Repo.clone_from(repo_url, dest, depth=1)
    else:
        print("Repo already cloned")

def walk_and_emit(repo_path, repo_name):
    print("Walking repo...")
    for path in Path(repo_path).rglob("*.*"):
        try:
            text = path.read_text(errors="ignore")
        except:
            continue

        doc = {
            "id": str(path),
            "path": str(path),
            "repo": repo_name,   # new field, extremely useful
            "code": text[:5000], # MATCH JAVA AND LUCENE
            "lang": path.suffix
        }

        r.xadd(STREAM, {"doc": json.dumps(doc)})
        print("Emitted:", path)

if __name__ == "__main__":
    repo = "https://github.com/spring-projects/spring-petclinic.git"
    repo_name = repo.split("/")[-1].replace(".git", "")   # <── Extract name

    clone_repo(repo)
    walk_and_emit("cloned_repo", repo_name)
# placeholder crawler