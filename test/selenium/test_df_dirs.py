
from selenium import selenium
import unittest
import sys
import getpass
import time

# note: use http:// to trigger shib login
# note: dev backed is annoying as it always asks for cert exception confirmation
# note: see README for creating and using custom firefox profile with cert exception
url = "https://srb.ac3.edu.au/ARCSDEVBACKEND/home/"
#url = "https://df.arcs.org.au/ARCS/home/"
username = "sean.fleming"

# Helpers --------------


# sync
def wait_for_ajax(browser):
# NB: the wait_for* sometimes works, but on some things (creating directories)
# it is not sufficient ... hence the sleep to provide a synchronization buffer
        time.sleep(1)
        browser.wait_for_condition("window.listingStore.isValid() == true;", 10000)


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
# NEW - get the unescaped string (thanks Rowan)
                 script = "var item = window.listingGrid.getItem(" + str(i) + ") ; window.decodeURIComponent(item.i.name.name);"
                 filename = browser.get_eval(script)
# debug
#                 print "- " + filename

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


# change directory
def change_directory(test, browser, name):
        locator = "link=" + name
        test.assertTrue(browser.is_element_present(locator))
        browser.click(locator)
        wait_for_ajax(browser)


# request directory creation
def create_directory(test, browser, name):
        test.assertTrue(browser.is_element_present("//table[@id='createDirectoryButton']//tr[1]/td[1]"))
        browser.click("//table[@id='createDirectoryButton']//tr[1]/td[1]")
        test.assertTrue(browser.is_element_present("//input[@name='directory']"))
        browser.type("//input[@name='directory']", name)
        test.assertTrue(browser.is_element_present("id=createDirCreateButton"))
        browser.click("id=createDirCreateButton")
        wait_for_ajax(browser)


# Tests -------------


def test_login(test, browser):
        browser.open(url);
        browser.wait_for_page_to_load(50000)
        test.assertTrue(browser.is_element_present("name=username"))
        test.assertTrue(browser.is_element_present("name=password"))
        test.assertTrue(browser.is_element_present("//input[@type='submit']"))
        browser.type("name=username", username)
        browser.type("name=password", password)
        browser.click("//input[@type='submit']")
        browser.wait_for_page_to_load(50000)
        wait_for_ajax(browser)

# TODO - add a test for the authentication failed message

# check for home button
        test.assertTrue(browser.is_element_present("//a[contains(@title,'home folder')]"))
# check for refresh button
        test.assertTrue(browser.is_element_present("//a[contains(@title,'Refresh')]"))


def test_navigate_home(test, browser):
        browser.click("//a[contains(@title,'home folder')]")
        wait_for_ajax(browser)



def test_create_directory(test, browser, name):
        create_directory(test, browser, name)
# FIXME - sync problems here?
#        time.sleep(5)
# assert directory now exists
        list = file_list(browser)
        test.assertTrue(name in list, "Failed to create directory: " + name)



def test_delete_directory(test, browser, name):
# get the index of item to delete
        list = file_list(browser)
        test.assertTrue(name in list, "Missing directory: " + name)
        i = list.index(name)
# select it
        js = "window.listingGrid.selection.selectRange(" + str(i) + "," + str(i) + ");"
        browser.get_eval(js);
        browser.get_eval("window.listingStore.refreshDisplay(false);");
        result = browser.get_eval("window.directoryIsSelected();");
# ensure default behaviour is to hit OK on cofirmation dialogs
        browser.choose_ok_on_next_confirmation()
# hit the Delete button
        test.assertTrue(browser.is_element_present("id=deleteButton"))
        browser.click("id=deleteButton");
# consume confirmation
        browser.get_confirmation()
# assert directory does not exist
        list = file_list(browser)
        test.assertFalse(name in list, "Failed to delete directory: " + name)




def test_rename_directory(test, browser, old, new):
# print will fail on unicode string
#        print "rename: " + old + " as: " + new
# get the index of item to delete
        list = file_list(browser)
        test.assertTrue(old in list, "rename source does not exist: " + old)
        test.assertFalse(new in list, "rename destination already exists: " + old)
        i = list.index(old)
# select it
        js = "window.listingGrid.selection.selectRange(" + str(i) + "," + str(i) + ");"
        browser.get_eval(js);
        browser.get_eval("window.listingStore.refreshDisplay(false);");
        result = browser.get_eval("window.directoryIsSelected();");
# hit the rename button
        test.assertTrue(browser.is_element_present("id=renameButton"))
        browser.click("id=renameButton");
# supply new name
        browser.type("//input[@id='renameInputBox']", new)

# CURRENT - can we simulate an <enter> keypress?
#        browser.keyDown("xpath=//input[@id='renameInputBox']", "\\13"); 
#        browser.type_keys("//input[@id='renameInputBox']", "\x13"); 
#        browser.type_keys("//input[@id='renameInputBox']", "\13"); 
#        browser.key_down("//input[@id='renameInputBox']", "\13"); 
#        browser.type_keys("//input[@id='renameInputBox']", "\n"); 
#        browser.key_down_native(10)
#        browser.key_press_native(13)

# couldn't figure out how to simulate an <enter> ... but this clicks the Rename button
        browser.click("//button[contains(.,'Rename')]")

# assert old name doesnt exist and new name doe
        list = file_list(browser)
        test.assertFalse(old in list, "Failed to rename old directory: " + old)
        test.assertTrue(new in list, "Failed to rename directory as: " + new)




# testing space ' " and unicode chars in directory names
def test_directory_operations(test, browser):
        list = ('te st', 'test"', 'test""', "test'", "test''", "test,", u"\u0165\u00e9\u015b\u0861")

        print "CURRENT - test creation of a bunch of odd named directories"

# create and change to a temp directory (so we don't clobber anything)
        name = file_unused(browser)
        test_create_directory(test, browser, name)
        change_directory(test, browser, name)

# TEST - create dir with funny chars
#        for item in list:
#            create_directory(test, browser, item)
# assert new directory listing contains all the items
#        new_list = file_list(browser)
#        for item in list:
#            test.assertTrue(item in new_list, 'Failed to create directory: ' + item)

# TEST - rename dir with funny chars
        for item in list:
            test_create_directory(test, browser, item)
            test_rename_directory(test, browser, item, "something_new")
            test_rename_directory(test, browser, "something_new", item)

# TEST - delete dir with funny chars
# leave this out for debug
#            test_delete_directory(test, browser, name):

# TODO - cleanup - go to home and remove temp directory


# NB - had to do it this way as unittest creates a new instance of the class
# for every test (ie setUp/tearDown applied around *every* test ... and I don't
# want to do a login for every type of test. Annoying thing is I couldn't seem
# to use a global variable to skip setUp after the 1st call ... not sure why
# there must be something about how unittest works I don't understand.
class test_datafabric(unittest.TestCase):
    def setUp(self):
        self.selenium = selenium("localhost", 4444, "*firefox", url)
        self.selenium.start()
 
# manually apply individual tests, so we don't have to login every time
    def test_suite(self):
        test_login(self, self.selenium)
        test_navigate_home(self, self.selenium)

#  test handling of directories 
        test_directory_operations(self, self.selenium) 

    def tearDown(self):
        self.selenium.stop()

if __name__ == "__main__":
    password = getpass.getpass(" Enter password for " + username + ": ")
    unittest.main()
