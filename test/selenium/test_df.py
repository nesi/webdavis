
import os
import sys
import time
import unittest
import getpass
import test_helpers
from selenium import selenium

# http:// to trigger shib login
# NB: for dev backend need to create a custom firefox profile with cert accepted
url = "https://srb.ac3.edu.au/ARCSDEVBACKEND/home/"
#url = "https://df.arcs.org.au/ARCS/home/"
username = ""
password = ""
upload = ""
browser = None
list = ('te st', 'test"', 'test""', "test'", "test''", "test(", "test)", "test,", u"\u0165\u00e9\u015b\u0861")

# -------

def login():
    browser.open(url);
    browser.wait_for_page_to_load(50000)
    browser.type("name=username", username)
    browser.type("name=password", password)
    browser.click("//input[@type='submit']")
    browser.wait_for_page_to_load(50000)

# -------

class test_datafabric(unittest.TestCase):
# all tests start assuming logged into home collection
    def setUp(self):
        test_helpers.navigate_home(self, browser)

# TODO - button functionality tests (trash logout etc)

#-------

    def test_file_operations(self):
# create and change to a temp directory (so we don't clobber anything)
        tmpdir = test_helpers.file_unused(browser)
        test_helpers.create_directory(self, browser, tmpdir)
        test_helpers.change_directory(self, browser, tmpdir)
# upload our file
        test_helpers.file_upload(self, browser, upload)
# copy file into directory
        test_helpers.create_directory(self, browser, "testdir")
        name = os.path.basename(upload)
# create copies in tmpdir with our list of odd names
        for item in list:
            test_helpers.rename(self, browser, name, item)
            test_helpers.file_copy_to_directory(self, browser, item, "testdir")
            test_helpers.rename(self, browser, item, name)
# check the copy worked, then delete
        test_helpers.change_directory(self, browser, "testdir")
        for item in list:
            test_helpers.delete_file(self, browser, item)
# cleanup
        test_helpers.navigate_home(self, browser)
        test_helpers.delete_directory(self, browser, tmpdir)

#-------

    def test_directory_operations(self):
# create and change to a temp directory (so we don't clobber anything)
        tmpdir = test_helpers.file_unused(browser)
        test_helpers.create_directory(self, browser, tmpdir)
        test_helpers.change_directory(self, browser, tmpdir)
# TEST - rename dir with funny chars
        for item in list:
            test_helpers.create_directory(self, browser, item)
            test_helpers.rename(self, browser, item, "something_new")
            test_helpers.rename(self, browser, "something_new", item)
            test_helpers.delete_directory(self, browser, item)
# cleanup
        test_helpers.navigate_home(self, browser)
        test_helpers.delete_directory(self, browser, tmpdir)

#-------

    def test_multiple_files(self):
        tmpdir = test_helpers.file_unused(browser)
        test_helpers.create_directory(self, browser, tmpdir)
        test_helpers.change_directory(self, browser, tmpdir)
        test_helpers.file_upload(self, browser, upload)
        base = prev = os.path.basename(upload)
        test_helpers.create_directory(self, browser, "multi_100")
        for i in range(1, 100):
            name = base + "_" + str(i)
            test_helpers.rename(self, browser, prev, name)
            test_helpers.file_copy_to_directory(self, browser, name, "multi_100")
            prev = name
# go into the directory and select all - delete - assert dont exist
        test_helpers.change_directory(self, browser, "multi_100")
        test_helpers.delete_all(self, browser)
# cleanup
        test_helpers.navigate_home(self, browser)
        test_helpers.delete_directory(self, browser, tmpdir)


if __name__ == "__main__":
# setup/prompt if unknown
    if (username == ""):
        username = raw_input("Enter username: ")
    if (password == ""):
        password = getpass.getpass("Enter password for " + username + ": ")
    if (upload == ""):
        upload = os.path.abspath(__file__)

# start selenium and fire off the tests
    server_ok = False
    try:
        browser = selenium("localhost", 4444, "*firefox", url)
        browser.start()
        server_ok = True
    except:
        print "Failed to start selenium, server not running?"

# TODO - fire it up if not running?
    if (server_ok == True):
        login()
        unittest.main()
        browser.stop()

