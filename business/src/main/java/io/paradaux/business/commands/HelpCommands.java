package io.paradaux.business.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.command.CommandSender;

@Singleton
@Command({"db", "democracybusiness", "business", "firm", "company"})
public class HelpCommands implements CommandHandler {

    private final Message message;

    @Inject
    public HelpCommands(Message message) {
        this.message = message;
    }

    @Route("")
    @Description("Show help")
    @Permission("business.help")
    public void root(@Sender CommandSender sender) {
        message.send(sender, "business.help");
    }

    @Route("help")
    @Description("Show help")
    @Permission("business.help")
    public void help(@Sender CommandSender sender) {
        root(sender);
    }

    @Route("help firms")
    @Description("Show help")
    @Permission("business.help")
    public void firmsHelp(@Sender CommandSender sender) {
        message.send(sender, "business.help.firms");
    }

    @Route("help staff")
    @Description("Show help")
    @Permission("business.help")
    public void staffHelp(@Sender CommandSender sender) {
        message.send(sender, "business.help.staff");
    }

    @Route("help requests")
    @Description("Show help")
    @Permission("business.help")
    public void requestsHelp(@Sender CommandSender sender) {
        message.send(sender, "business.help.requests");
    }

    @Route("help roles")
    @Description("Show help")
    @Permission("business.help")
    public void rolesHelp(@Sender CommandSender sender) {
        message.send(sender, "business.help.roles");
    }

    @Route("help accounts")
    @Description("Show help")
    @Permission("business.help")
    public void accountsHelp(@Sender CommandSender sender) {
        message.send(sender, "business.help.accounts");
    }

    @Route("help misc")
    @Description("Show help")
    @Permission("business.help")
    public void miscHelp(@Sender CommandSender sender) {
        message.send(sender, "business.help.misc");
    }
}