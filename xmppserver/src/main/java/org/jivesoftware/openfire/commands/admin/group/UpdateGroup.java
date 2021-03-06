/*
 * Copyright (C) 2005-2008 Jive Software, 2022 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.commands.admin.group;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command that allows to update a given group.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class UpdateGroup extends AdHocCommand {
    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        if (data.getStage() == 0) {
            form.setTitle("Update group configuration");
            form.addInstruction("Fill out this form to specify the group to update.");

            FormField field = form.addField();
            field.setType(FormField.Type.hidden);
            field.setVariable("FORM_TYPE");
            field.addValue("http://jabber.org/protocol/admin");

            field = form.addField();
            field.setType(FormField.Type.text_single);
            field.setLabel("Group Name");
            field.setVariable("group");
            field.setRequired(true);
        }
        else {

            // Check if groups cannot be modified (backend is read-only)
            if (GroupManager.getInstance().isReadOnly()) {
                Element note = command.addElement("note");
                note.addAttribute("type", "error");
                note.setText("Groups are read only");
                return;
            }
            // Get requested group
            Group group;
            try {
                group = GroupManager.getInstance().getGroup(data.getData().get("group").get(0));
            } catch (GroupNotFoundException e) {
                // Group not found
                Element note = command.addElement("note");
                note.addAttribute("type", "error");
                note.setText("Group not found");
                return;
            }

            form.setTitle("Update group configuration");
            form.addInstruction("Fill out this form with the new group configuration.");

            FormField field = form.addField();
            field.setType(FormField.Type.hidden);
            field.setVariable("FORM_TYPE");
            field.addValue("http://jabber.org/protocol/admin");

            field = form.addField();
            field.setType(FormField.Type.text_multi);
            field.setLabel("Description");
            field.setVariable("desc");
            if (group.getDescription() != null) {
                field.addValue(group.getDescription());
            }

            field = form.addField();
            field.setType(FormField.Type.list_single);
            field.setLabel("Shared group visibility");
            field.setVariable("showInRoster");
            field.addValue("nobody");
            field.addOption("Disable sharing group in rosters", "nobody");
            field.addOption("Show group in all users' rosters", "everybody");
            field.addOption("Show group in group members' rosters", "onlyGroup");
            field.addOption("Show group to members' rosters of these groups", "spefgroups");
            field.setRequired(true);
            if (group.getSharedWith() != null) {
                final String showInRoster;
                switch (group.getSharedWith()) {
                    case nobody:
                        showInRoster = "nobody";
                        break;
                    case everybody:
                        showInRoster = "everybody";
                        break;
                    case usersOfGroups:
                        final List<String> sharedWith = group.getSharedWithUsersInGroupNames();
                        if (sharedWith.isEmpty() || (sharedWith.size() == 1 && sharedWith.contains(group.getName()))) {
                            showInRoster = "onlyGroup";
                        } else {
                            showInRoster = "spefgroups";
                        }
                        break;
                    default:
                        showInRoster = group.getSharedWith().toString();
                        break;
                }
                field.addValue(showInRoster);
            }

            field = form.addField();
            field.setType(FormField.Type.list_multi);
            field.setVariable("groupList");
            for (Group otherGroup : GroupManager.getInstance().getGroups()) {
                field.addOption(otherGroup.getName(), otherGroup.getName());
            }
            final List<String> groupList = group.getSharedWithUsersInGroupNames();
            for (final String othergroup : groupList) {
                field.addValue(othergroup);
            }

            field = form.addField();
            field.setType(FormField.Type.text_single);
            field.setLabel("Group Display Name");
            field.setVariable("displayName");
            String displayName = group.getSharedDisplayName();
            if (displayName != null) {
                field.addValue(displayName);
            }
        }

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        // Get requested group
        Group group;
        try {
            group = GroupManager.getInstance().getGroup(data.getData().get("group").get(0));
        } catch (GroupNotFoundException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("Group not found");
            return;
        }

        List<String> desc = data.getData().get("desc");
        if (desc != null) {
            group.setDescription(desc.get(0));
        }

        String showInRoster = data.getData().get("showInRoster").get(0);
        String displayName;
        if (data.getData().get("displayName") != null && !data.getData().get("displayName").isEmpty()) {
            displayName = data.getData().get("displayName").get(0);
        } else {
            displayName = group.getSharedDisplayName();
        }
        List<String> groupList = data.getData().get("groupList");

        switch (showInRoster) {
            case "nobody":
                // New group is not a shared group
                group.shareWithNobody();
                break;

            case "everybody":
                if (displayName != null ) {
                    group.shareWithEverybody(displayName);
                }
                break;

            case "spefgroups":
                if (displayName != null ) {
                    group.shareWithUsersInGroups(groupList, displayName);
                }
                break;

            case "onlyGroup":
                if (displayName != null ) {
                    group.shareWithUsersInSameGroup(displayName);
                }
                break;
        }

        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#update-group";
    }

    @Override
    public String getDefaultLabel() {
        return "Update group configuration";
    }

    @Override
    protected List<Action> getActions(SessionData data) {
        if (data.getStage() == 0) {
            return Collections.singletonList(Action.next);
        }
        else if (data.getStage() == 1) {
            return Arrays.asList(AdHocCommand.Action.next, AdHocCommand.Action.prev, AdHocCommand.Action.complete);
        }
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        if (data.getStage() == 0) {
            return AdHocCommand.Action.next;
        }
        return AdHocCommand.Action.complete;
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 2;
    }
}
