
import os
import sys
import time
import unittest


# sync
def wait_for_ajax(browser):
# trap unexpected alerts 
    if (browser.is_alert_present()):
        browser.get_alert()
    if (browser.is_confirmation_present()):
        browser.get_confirmation()
# NB: the wait_for* sometimes works, but on some things (creating directories)
# it is not sufficient ... hence the sleep to provide a synchronization buffer
# even got an exception with window.listingStore undefined ...
# NB: this still seems to be needed - even with the new sleep polling
    time.sleep(1)

    browser.wait_for_condition("window.listingStore.isValid() == true;", 50000)
# this doesn't seem overly successful as an additional sync method
    browser.wait_for_condition("window.listingGrid.getItem(0) != null;", 50000)


# new style sync
def alt_wait(browser):
    browser.wait_for_condition("window.listingStore.isValid() == false;", 50000)
    browser.wait_for_condition("window.listingStore.isValid() == true;", 50000)


# simulate hitting the refresh button
def file_list_refresh(browser):
    wait_for_ajax(browser)
    browser.click("//a[contains(@title,'Refresh')]")
    wait_for_ajax(browser)


# get current directory listing
def file_list(browser):
    wait_for_ajax(browser)
    i = 0
    list = []
    while 1:
         script = "window.listingGrid.getItem(" + str(i) + ")"
         object = browser.get_eval(script)
         if (object != "null"):
             script = "var item = window.listingGrid.getItem(" + str(i) + ") ; window.decodeURIComponent(item.i.name.name);"
             filename = browser.get_eval(script)
             list.append(filename)
         else:
             break
         i = i + 1
    return list


# get an unused filename
def file_unused(browser):
    list = file_list(browser)
    i = 0
    base = "gok"
    name = base;
    while 1:
        if name in list:
            name = base + str(i)
        else:
            break
        i = i+1
    return name


# select (highlight) an item ie don't follow link
def select_item(test, browser, name):
#    print "Selecting: " + name.encode('utf-8')
# get the index of item
    list = file_list(browser)
    test.assertTrue(name in list, "Missing item: " + name)
    i = list.index(name)
# select it
    js = "window.listingGrid.selection.selectRange(" + str(i) + "," + str(i) + ");"
    browser.get_eval(js);
    browser.get_eval("window.listingStore.refreshDisplay(false);");
    wait_for_ajax(browser)


# click the select all button
def select_all(test, browser):
    wait_for_ajax(browser)
    browser.click("id=toggleAllButton");
    wait_for_ajax(browser)


# get listing from the copy destination window
def destination_list(browser):
    wait_for_ajax(browser)
    i = 0
    list = []
    while 1:
         script = "window.destinationGrid.getItem(" + str(i) + ")"
         object = browser.get_eval(script)
         if (object != "null"):
             script = "var item = window.destinationGrid.getItem(" + str(i) + ") ; window.decodeURIComponent(item.i.name.name);"
             filename = browser.get_eval(script)
             list.append(filename)
         else:
             break
         i = i + 1
    return list


# copy file into a directory in the current directory
# FIXME - the sync needs major work here
def file_copy_to_directory(test, browser, filename, dirname):
# select testfile
    select_item(test, browser, filename)
# pop up copy dialog
    browser.click("id=copyButton")
    wait_for_ajax(browser)
# navigate into temp directory
    browser.wait_for_condition("window.destinationStore.isValid() == true;", 10000)


# FIXME - sleep not good enough
# CURRENT - experimenting to see if it really is a sync problem
# CURRENT - sync problem workaround
    for i in range(1,30):
        dirs = destination_list(browser)
        if (dirname in dirs):
            break
        time.sleep(2)
    
    dirs = destination_list(browser)
    test.assertTrue(dirname in dirs, "Missing directory: " + dirname)

# CURRENT - this seems to fix the problem in some cases ... so it's probably an ajax issue again
    time.sleep(2)

# FIXME - sync issue here that needs to be fixed (see above)
    locator = "//div[@id='dojox_grid__View_4']//a[contains(@onclick,'/" + dirname + "')]"
    browser.click(locator)


# FIXME - sleep not good enough
    wait_for_ajax(browser)
    browser.wait_for_condition("window.destinationStore.isValid() == true;", 10000)
#    time.sleep(2)
# trigger the copy
# TODO - assert it's become active?
    browser.click("id=destinationMoveCopyButton")
# assert it's there ... test at higher level (since we'd need to navigate into it)
    wait_for_ajax(browser)


# change directory
def change_directory(test, browser, name):
    wait_for_ajax(browser)
    locator = "link=" + name
    browser.click(locator)
    wait_for_ajax(browser)


# request directory creation
def create_directory(test, browser, name):
    wait_for_ajax(browser)
    browser.click("//table[@id='createDirectoryButton']//tr[1]/td[1]")
    browser.type("//input[@name='directory']", name)
    browser.click("id=createDirCreateButton")
    wait_for_ajax(browser)
# assert directory now exists
# CURRENT - this sometimes reports an error when the create works, ajax again probably
# CURRENT - sync problem workaround
    for i in range(1,30):
        list = file_list(browser)
        if (name in list):
            break
        time.sleep(2)
    test.assertTrue(name in list, "Failed to create directory: " + name)


def navigate_home(test, browser):
    wait_for_ajax(browser)
#    time.sleep(2)
    browser.click("//a[contains(@title,'home folder')]")
#    time.sleep(2)
    wait_for_ajax(browser)


def delete_file(test, browser, name):
#    print "deleting: " + name.encode('utf-8')
    select_item(test, browser, name)
    wait_for_ajax(browser)
# hit the Delete button
    browser.click("id=deleteButton");

# CURRENT - sync problem workaround
    for i in range(1,30):
        list = file_list(browser)
        if (name in list):
            time.sleep(2)
        else:
            break
# assert no longer exists
    test.assertFalse(name in list, "Failed to delete file: " + name.encode('utf-8'))


# main difference from delete_file is confirmation
# which can be consumed (if exists) by wait_for_ajax
def delete_directory(test, browser, name):
    select_item(test, browser, name)
#    result = browser.get_eval("window.directoryIsSelected();");
    wait_for_ajax(browser)
# ensure default behaviour is to hit OK on confirmation dialogs
    browser.choose_ok_on_next_confirmation()
# hit the Delete button
    browser.click("id=deleteButton");

# consume confirmation
    wait_for_ajax(browser)
#    if (browser.is_confirmation_present()):
#        browser.get_confirmation()

# CURRENT - sync problem workaround
    for i in range(1,30):
        list = file_list(browser)
        if (name in list):
            time.sleep(2)
        else:
            break
    test.assertFalse(name in list, "Failed to delete dir: " + name.encode('utf-8'))


def delete_all(test, browser):
    list_before = file_list(browser)
    list_before.remove('... Parent Directory')

    select_all(test, browser)

    browser.click("id=deleteButton")

# CURRENT - new poll/delay here
    alt_wait(browser)

    list_after = file_list(browser)
    list_after.remove('... Parent Directory')
    for item in list_after:
        test.assertFalse(item in list_before, "Failed to delete dir: " + item)


# works for files and directories
def rename(test, browser, old, new):
# print will fail on unicode string
#        print "rename: " + old.encode('utf-8') + " as: " + new.encode('utf-8')
# get the index of item to delete
    list = file_list(browser)
    test.assertTrue(old in list, "rename source does not exist: " + old.encode('utf-8'))
    test.assertFalse(new in list, "rename destination already exists: " + new.encode('utf-8'))
    select_item(test, browser, old)
# hit the rename button
    browser.click("id=renameButton");
# supply new name
    browser.type("//input[@id='renameInputBox']", new)
# couldn't figure out how to simulate an <enter> ... but this clicks the Rename button
    browser.click("//button[contains(.,'Rename')]")

# CURRENT - this sometimes reports an error when the rename works, ajax again probably
# CURRENT - same type of error in file_copy_to_directory
# CURRENT - sync problem workaround
    for i in range(1,30):
        list = file_list(browser)
        if (new in list):
            break
        time.sleep(2)
    test.assertTrue(new in list, "Failed to rename: " + old.encode('utf-8') + " as: " + new.encode('utf-8'))


def file_upload(test, browser, filename):
    browser.click("id=uploadButton")
    wait_for_ajax(browser)
    browser.type("//input[@id='uploadTextField']", filename)

# trigger the upload
    browser.click("id=uploadStartButton")

# CURRENT - sync/wait problem workaround
    for i in range(1,30):
        list = file_list(browser)
        if (os.path.basename(filename) in list):
            break
        time.sleep(5)

# assert file has been uploaded
    list = file_list(browser)
    test.assertTrue(os.path.basename(filename) in list, "Failed to upload: " + filename)

