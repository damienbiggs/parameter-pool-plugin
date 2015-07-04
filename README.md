# parameter-pool-plugin
Jenkins plugin for selecting a unique parameter from a parameter pool

Plugin adds a build step for setting a pool of values for a paramter.
Ensures that a unique value will be used for each concurrent build.
E.g. no two running builds will use the same parameter value.

I needed to create something like this for our CI environment.
We have test VMs that we set up then run tests against on a per commit basis.
These VMs are separate to slaves.
To run these tests concurrently, I needed to ensure that two test jobs wouldn't run against the same test VM.

We use the following flow.

After a code commit, the following runs

1) A container tests job is kicked off<br/>
2) This job uses the parameter pool plugin to select a vm name that is not in use in any other running container jobs<br/>
3) This vm is reverted<br/>
4) This vm is rebuilt with the code from the commit<br/>
5) Tests are run against that vm.
