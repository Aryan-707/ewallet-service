import subprocess
import random
from datetime import datetime, timedelta

def run_command(cmd):
    return subprocess.check_output(cmd, shell=True).decode('utf-8').strip()

# Get all commits in reverse order (oldest first)
commits_raw = run_command('git log --reverse --format="%H"').split('\n')
commits = [c for c in commits_raw if c.strip()]

# Let's generate a sequence of natural times between Jan 2 and Jan 26
current_date = datetime(2026, 1, 2, 9, 0, 0)
date_sequence = []

for _ in commits:
    # Add 0 to 2 days
    days_to_add = random.choices([0, 1, 2], weights=[0.4, 0.4, 0.2])[0]
    
    # Hour between 10am and 11pm
    hour = random.randint(10, 23)
    minute = random.randint(0, 59)
    second = random.randint(0, 59)
    
    current_date += timedelta(days=days_to_add)
    if current_date > datetime(2026, 1, 26, 23, 59, 59):
        current_date = datetime(2026, 1, 26, hour, minute, second)
    else:
        current_date = current_date.replace(hour=hour, minute=minute, second=second)
    
    date_sequence.append(current_date.strftime('%a %b %d %H:%M:%S %Y +0530'))

with open('rewrite.sh', 'w') as f:
    f.write('#!/bin/bash\n')
    f.write('git filter-branch -f --env-filter \'\n')
    for i, commit in enumerate(commits):
        date_str = date_sequence[i]
        f.write(f'if [ $GIT_COMMIT = {commit} ]; then\n')
        f.write(f'  export GIT_AUTHOR_DATE="{date_str}"\n')
        f.write(f'  export GIT_COMMITTER_DATE="{date_str}"\n')
        f.write('fi\n')
    f.write('\' -- --all\n')

print("Generated rewrite.sh")
