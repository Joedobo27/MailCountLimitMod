package com.joedobo27.mcl;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.WurmMail;
import com.wurmonline.server.players.PlayerInfo;
import javassist.*;
import javassist.bytecode.Descriptor;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;


public class MailCountLimitMod implements WurmServerMod, Initable, Configurable, PlayerMessageListener {

    private static int maxMailCount = 100;
    private static final Logger logger = Logger.getLogger(MailCountLimitMod.class.getName());
    private static final ClassPool pool = HookManager.getInstance().getClassPool();

    @Override
    public boolean onPlayerMessage(Communicator communicator, String s) {
        return false;
    }

    @Override
    public MessagePolicy onPlayerMessage(Communicator communicator, String message, String title) {
        if (communicator.getPlayer().getPower() == 5 && message.startsWith("/MailCountLimitMod properties")) {
            communicator.getPlayer().getCommunicator().sendNormalServerMessage(
                    "Reloading properties for MailCountLimitMod.");
            Properties properties = getProperties();
            if (properties == null) {
                communicator.getPlayer().getCommunicator().sendNormalServerMessage(
                        "Problem getting properties and nothing was changed.");
                return MessagePolicy.DISCARD;
            }
            maxMailCount = Integer.parseInt(properties.getProperty("maxMailCount", String.valueOf(maxMailCount)));
            return MessagePolicy.DISCARD;
        }
        return MessagePolicy.PASS;
    }

    @Override
    public void configure(Properties properties) {
        maxMailCount = Integer.parseInt(properties.getProperty("maxMailCount", Integer.toString(maxMailCount)));
    }

    @Override
    public void init() {
        try {
            CtClass ctMailSendQuestion = pool.get("com.wurmonline.server.questions.MailSendQuestion");
            CtMethod ctAnswer = ctMailSendQuestion.getMethod("answer", Descriptor.ofMethod(
                    CtPrimitiveType.voidType, new CtClass[]{pool.get("java.util.Properties")}));

            ByteCodeWild byteCodeWild = new ByteCodeWild(ctMailSendQuestion.getClassFile().getConstPool(),
                    ctAnswer.getMethodInfo().getCodeAttribute());
            byteCodeWild.addAload("name", "Ljava/lang/String;");
            byteCodeWild.addInvokestatic("com/wurmonline/server/players/PlayerInfoFactory",
                    "createPlayerInfo",
                    "(Ljava/lang/String;)Lcom/wurmonline/server/players/PlayerInfo;");
            byteCodeWild.addAstore("pinf", "Lcom/wurmonline/server/players/PlayerInfo;");
            byteCodeWild.trimFoundBytecode();
            int insertLine = byteCodeWild.getTableLineNumberAfter();

            ctAnswer.insertAt(insertLine, "if(!com.joedobo27.mcl.MailCountLimitMod.countMailedItems(pinf, " +
                    "this.mailbox, this.getResponder(), name)) { return;}");

        }catch (NotFoundException | CannotCompileException e){
            logger.warning(e.getMessage());
        }
    }

    private static Properties getProperties() {
        try {
            File configureFile = new File("mods/MailCountLimitMod.properties");
            FileInputStream configureStream = new FileInputStream(configureFile);
            Properties configureProperties = new Properties();
            configureProperties.load(configureStream);
            return configureProperties;
        } catch (IOException e) {
            logger.warning(e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unused")
    static public boolean countMailedItems(PlayerInfo playerInfo, Item mailbox, Creature player, String name) {
        Set<WurmMail> setWurmMails = com.wurmonline.server.items.WurmMail.getMailsFor(playerInfo.wurmId);
        WurmMail[] wurmMails = new WurmMail[setWurmMails.size()];
        setWurmMails.toArray(wurmMails);
        int inTransitCount = 0;
        for (WurmMail wurmMail: wurmMails) {
            Item item;
            try {
                item = Items.getItem(wurmMail.itemId);
            } catch (NoSuchItemException e) {
                logger.warning(e.getMessage());
                return false;
            }
            inTransitCount += item.getAllItems(false).length;
        }
        if (inTransitCount + mailbox.getAllItems(false).length > maxMailCount) {
            player.getCommunicator().sendNormalServerMessage(String.format(
                    "%s would have %d items in transit which exceed the max of %d.", name,
                    inTransitCount + mailbox.getAllItems(false).length, maxMailCount));
            return false;
        }
        return true;
    }
}
