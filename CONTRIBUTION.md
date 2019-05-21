Branching names:
---
Each branch must have a name in the following format with a proper prefix and number of Github's ticket it refers to:

```
prefix/000-branch-name
``` 

Allowed prefixes:
* feature
* bugfix
* docs
* test (when adding missing tests)
* ...

Commits:
---
Each commit (except for merge commits) must have a name in the following format:

```
000 - description of what was changed
```

Pull requests:
---
Each pull request must have a name in the following format where `000` represents a ticket number which this PR refers 
to:
```
000 - description of what was changed
```

Each pull request must have a nice description of what was changed. It must have 
information about all necessary actions need to be taken during deployment like database migrations or additional 
configuration.

Pull requests should be as concise as possible to make them easier to review. If your PR gets bigger and verbose, 
consider slice it into a few smaller but fully functional ones.

The description should end with a list of all corresponding tickets it closes or refers to.