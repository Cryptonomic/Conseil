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
* improvement
* refactor
* chores (for those tasks that needs be done for infrastructure compliance, some policy change)
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
000(-1) - description of what was changed
```

When you'are creating more than one PR for a ticket you should use postfixes like `-1`, `-2` to define order of merging.

Each pull request must have a nice description of what was changed. The description should start with a list of all 
corresponding tickets it closes or refers to. Then it must have information about all necessary actions need to be taken 
during deployment like database migrations or additional configuration.

Pull requests should be as concise as possible to make them easier to review. If your PR gets bigger and verbose, 
consider slice it into a few smaller but fully functional ones.