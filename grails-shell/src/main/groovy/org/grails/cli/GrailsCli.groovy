package org.grails.cli

import grails.build.logging.GrailsConsole
import grails.util.Environment
import groovy.transform.CompileStatic;
import jline.console.ConsoleReader;
import jline.console.completer.AggregateCompleter
import jline.console.completer.Completer

import org.codehaus.groovy.grails.cli.parsing.CommandLine
import org.codehaus.groovy.grails.cli.parsing.CommandLineParser

@CompileStatic
class GrailsCli {
    List<CommandLineHandler> commandLineHandlers=[]
    AggregateCompleter aggregateCompleter=new AggregateCompleter()
    CommandLineParser cliParser = new CommandLineParser()
    boolean keepRunning = true
    
    public int run(String... args) {
        ProfileRepository profileRepository=new ProfileRepository()
        File applicationProperties=new File("application.properties")
        if(!applicationProperties.exists()) {
            print "not exists..."
            if(!args) {
                println "usage: create-app appname --profile=web"
                return 1
            }
            if(args[0] == 'create-app') {
                String appname = args[1]
                String profile=null
                if(args.size() > 2) {
                    def matches = (args[2] =~ /^--profile=(.*?)$/)
                    if (matches) {
                        profile=matches.group(1)
                    }
                }
                println "app: $appname profile: $profile"
                CreateAppCommand cmd = new CreateAppCommand(profileRepository: profileRepository, appname: appname, profile: profile)
                cmd.run()
            }
        } else {
            Profile profile = profileRepository.getProfile('web')
            commandLineHandlers.addAll(profile.getCommandLineHandlers() as Collection)
            aggregateCompleter.getCompleters().addAll((profile.getCompleters()?:[]) as Collection)
        
            CommandLine mainCommandLine=cliParser.parse(args)
            def commandName = mainCommandLine.getCommandName()
            if(commandName) {
                handleCommand(mainCommandLine, GrailsConsole.getInstance())
            } else {
                System.setProperty(Environment.INTERACTIVE_MODE_ENABLED, "true")
                GrailsConsole console=GrailsConsole.getInstance()
                console.reader.addCompleter(aggregateCompleter)
                console.println("Starting interactive mode...")
                while(keepRunning) {
                    String commandLine = console.showPrompt()
                    handleCommand(cliParser.parseString(commandLine), console)
                }
            }
        }
        return 0
    }
    
    boolean handleCommand(CommandLine commandLine, GrailsConsole console) {
        if(handleBuiltInCommands(commandLine, console)) {
            return true
        }
        for(CommandLineHandler handler : commandLineHandlers) {
             if(handler.handleCommand(commandLine, console)) {
                 return true
             }
        }
        console.error("Command not found ${commandLine.commandName}")
        return false
    }

    private boolean handleBuiltInCommands(CommandLine commandLine, GrailsConsole console) {
        switch(commandLine.getCommandName()) {
            case 'help':
                List<CommandDescription> allCommands=findAllCommands()
                String remainingArgs = commandLine.getRemainingArgsString()
                if(remainingArgs?.trim()) {
                    CommandLine remainingArgsCommand = cliParser.parseString(remainingArgs)
                    String helpCommandName = remainingArgsCommand.getCommandName()
                    for (CommandDescription desc : allCommands) {
                        if(desc.name == helpCommandName) {
                            console.println "${desc.name}\t${desc.description}\n${desc.usage}"
                            return true
                        }
                    }
                    console.error "Help for command $helpCommandName not found"
                    return false
                } else {
                    for (CommandDescription desc : allCommands) {
                        console.println "${desc.name}\t${desc.description}"
                    }
                    console.println("detailed usage with help [command]")
                    return true
                }
                break
            case 'exit':
                exitInteractiveMode()
                return true
                break
        }
        return false
    }
    
    private void exitInteractiveMode() {
        keepRunning = false
    }

    private List<CommandDescription> findAllCommands() {
        List<CommandDescription> allCommands=[]
        for(CommandLineHandler handler : commandLineHandlers) {
            allCommands.addAll((handler.listCommands() ?: []) as Collection)
        }
        allCommands
    }
    
    public static void main(String[] args) {
        GrailsCli cli=new GrailsCli()
        System.exit(cli.run(args))
    }
}