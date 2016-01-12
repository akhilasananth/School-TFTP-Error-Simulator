# TFTP Git Quick Commands

## Getting Started
1. Open terminal/powershell/cmd
1. cd to a directory to work from
1. git clone <repo name>
1. Start an eclipse workspace from this location
1. Go to File > Import > Existing Projects Into Workspace > Select root director: 
1. Enter the TFTP root directory path here
1. Finish

Now you have the project :)

## Terminology
branching - creating a separate patch for a changeset 

commit - finalize code for submitting to the main code base

fetch - fetch the latest changes from the repository

rebase - including all other people's commit history to your git branch

pull - a automatic fetch & merge & rebase 

merge - merging code from between two similar files with changes


## Commands 
I'm familiarized myself with using the command line alone. 
I suggest downloding Source Tree as it provides a graphical GUI
for everything the command line does:
https://www.sourcetreeapp.com/ 

The hash symbol is a comment.

These are a list of most frequently used commands.

### Before you start
Get the latest codebase from the repository

1. git checkout master # bring you into your master branch
1. git fetch origin/master # gets the latest code base
1. git rebase master # updates your branch with other people's commits

### Before you begin making changes
Check which branch you are on, and if you're on master branch,
then make sure you create a new branch to work in.

1. git branch # tells your the branch you're omn
1. git checkout -b branchName # creates a new branch with <branchName>
1. git branch # tell you your current branch is now branchName

### When you're done coding
These procedures will show you how to submit code to the repository

1. git branch # this BETTER not have a * next to master branch, if it does... then we need to fix it
1. git status # show what files have been changed
1. git diff   # shows the differences in the files you have made
1. git add --all # add all the files you've changed to the commit
1. git commit -m "Your commit message (what you changed)"
1. git push origin branchName # branchName is the current branch you are working on

After you pushed, you must go onto the github repository website and make a
**pull request**. 

### Creating a pull request
This is a formal way to declare you have finished with your changes
and create a code review ticket for your peers to approve. Once
This request is approved, you changes will be merged into the
master branch.

1. Go to the project repository web page
1. Click create pull request button

### Fixing merge conflicts
This activity is quite delicate and must be treated with care or
risk losing code
