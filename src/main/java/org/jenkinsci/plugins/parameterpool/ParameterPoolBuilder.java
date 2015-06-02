package org.jenkinsci.plugins.parameterpool;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link ParameterPoolBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class ParameterPoolBuilder extends Builder {

    private final String projects;

    private final String name;

    private final String values;

    private final boolean preferError;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ParameterPoolBuilder(String projects, String name, String values, boolean preferError) {
        this.projects = projects;
        this.name = name;
        this.values = values;
        this.preferError = preferError;
    }

    public String getProjects() {
        return projects;
    }

    public String getName() {
        return name;
    }

    public String getValues() {
        return values;
    }

    public boolean isPreferError() {
        return preferError;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException,
            InterruptedException {

        List<AbstractProject> projectsToUse = new ArrayList<AbstractProject>();
        if (StringUtils.isBlank(projects)) {
            projectsToUse.add(build.getProject());
        } else {
            for (String potentialName : projects.split(",")) {
                AbstractProject matchingProject = AbstractProject.findNearest(potentialName);
                if (matchingProject == null) {
                    throw new IllegalArgumentException("Project name " + potentialName + " was not found!");
                }
                projectsToUse.add(matchingProject);
            }
        }


        List<Run> builds = new ArrayList<Run>();
        for (AbstractProject project : projectsToUse) {
            builds.addAll(project.getBuilds());
        }

        Collections.sort(builds, new Comparator<Run>() {
            @Override
            public int compare(Run firstRun, Run secondRun) {
                return new Long(secondRun.getStartTimeInMillis()).compareTo(firstRun.getStartTimeInMillis());
            }
        });

        PrintStream logger = listener.getLogger();
        EnvVars env = build.getEnvironment(listener);

        String expandedName = env.expand(name);
        String expandedValues = env.expand(values);

        ParameterParser parameterParser = new ParameterParser(expandedValues);

        if (parameterParser.getValues().isEmpty()) {
            throw new IllegalArgumentException("No values set for name " + expandedName);
        }

        logger.println("Parsed following values from input text " + expandedValues);
        logger.println(parameterParser.valuesAsText());


        String selectedPoolValue = selectPoolValue(build.getNumber(), builds, logger,
                parameterParser.getValues());

        logger.println("Adding " + expandedName + " as environment variable with value of " + selectedPoolValue);

        ParameterEnvAction envAction = new ParameterEnvAction();
        envAction.add(expandedName, selectedPoolValue);

        build.addAction(envAction);

        return true;
    }

    private String selectPoolValue(int currentBuildNumber, List<Run> builds, PrintStream logger,
                                   Set<String> allowedValues) {
        BuildPoolValues poolValues = new BuildPoolValues();
        int completedBuildsChecked = 0;
        for (Run build : builds) {
            int buildNumber = build.getNumber();
            if (buildNumber == currentBuildNumber) {
                continue;
            }

            // there could be running builds further before completed builds
            if (completedBuildsChecked > 20) {
                break;
            }
            Result result = build.isBuilding() ? Result.NOT_BUILT : build.getResult();
            if (result != Result.NOT_BUILT) {
                completedBuildsChecked ++;
            }

            String poolValue = null;
            ParameterEnvAction parameterEnvAction = build.getAction(ParameterEnvAction.class);
            if (parameterEnvAction != null) {
                poolValue = parameterEnvAction.getValue(name);
            }

            logger.println("Build number " + buildNumber + ", param value: " + poolValue + ", result: " + result.toString());
            if (parameterEnvAction == null) {
                logger.println("No " + ParameterEnvAction.class.getSimpleName() + " found for build " + buildNumber);
                continue;
            }

            if (poolValue == null) {
                logger.println("No value named " + name + " added in build " + buildNumber);
                logger.println("Pool parameters in build: " + parameterEnvAction.getNames().toString());
                continue;
            }
            poolValues.addPoolValue(result, poolValue);
        }
        poolValues.printValues(logger);

        String value = poolValues.selectValue(allowedValues, preferError);
        if (value == null) {
            throw new IllegalArgumentException("No allowable value found! All of these values were taken: "
                    + allowedValues.toString());
        }
        return value;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }


    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a parameter name");
            if (value.length() < 2)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public FormValidation doCheckValues(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set parameter values");
            return FormValidation.ok();
        }

        /**
         * Form validation method.
         *
         * Copied from hudson.tasks.BuildTrigger.doCheck(Item project, String value)
         */
        public FormValidation doCheckProjects(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value ) {
            // Require CONFIGURE permission on this project
            if(!project.hasPermission(Item.CONFIGURE)){
                return FormValidation.ok();
            }
            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value),",");
            boolean hasProjects = false;
            while(tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (StringUtils.isNotBlank(projectName)) {
                    Item item = Jenkins.getInstance().getItem(projectName,project,Item.class); // only works after version 1.410
                    if(item==null){
                        return FormValidation.error("Project name " + projectName + " not found, did you mean "
                                + AbstractProject.findNearest(projectName).getName());
                    }
                    if(!(item instanceof AbstractProject)){
                        return FormValidation.error("Project " + projectName + " is not buildable");
                    }
                    hasProjects = true;
                }
            }
            if (!hasProjects) {
//            	return FormValidation.error(Messages.BuildTrigger_NoProjectSpecified()); // only works with Jenkins version built after 2011-01-30
                return FormValidation.error("No project specified");
            }

            return FormValidation.ok();
        }

        /**
         * Autocompletion method
         *
         * Copied from hudson.tasks.BuildTrigger.doAutoCompleteChildProjects(String value)
         *
         * @param value
         * @return
         */
        public AutoCompletionCandidates doAutoCompleteProjects(@QueryParameter String value, @AncestorInPath ItemGroup context) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<Job> jobs = Jenkins.getInstance().getAllItems(Job.class);
            for (Job job: jobs) {
                String relativeName = job.getRelativeNameFrom(context);
                if (relativeName.startsWith(value)) {
                    if (job.hasPermission(Item.READ)) {
                        candidates.add(relativeName);
                    }
                }
            }
            return candidates;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Parameter Pool";
        }

    }

    private static class ParameterEnvAction implements EnvironmentContributingAction {
        // Decided not to record this data in build.xml, so marked transient:
        private Map<String,String> data = new HashMap<String,String>();

        private void add(String key, String val) {
            if (data==null) return;
            data.put(key, val);
        }

        public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
            if (data!=null) env.putAll(data);
        }

        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }

        public String getValue(String name) {
            return data != null ? data.get(name) : null;
        }

        public Set<String> getNames() {
            return data != null ? data.keySet() : new HashSet<String>();
        }
    }
}

