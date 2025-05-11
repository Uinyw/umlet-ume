[![Build Status](https://api.travis-ci.org/umlet/umlet.svg?branch=master)](https://travis-ci.org/umlet/umlet) [![Java CI with Maven](https://github.com/umlet/umlet/actions/workflows/maven.yml/badge.svg)](https://github.com/umlet/umlet/actions/workflows/maven.yml)
# UMLet
UMLet is an open-source UML tool with a simple user interface: draw UML diagrams fast, export diagrams to eps, pdf, jpg, svg, and clipboard, share diagrams using Eclipse, and create new, custom UML elements. 

* Please check out the [Wiki](https://github.com/umlet/umlet/wiki) for frequently asked questions

* Go to http://www.umlet.com to get the latest compiled versions or to http://www.umletino.com to use UMLet in your web browser

## Steps to Generate Plugin:
Execute `mvn install`.\
Access plugin in `umlet-vscode/target`.

## Problem Solving
Set gen source file for `umlet-elements` to `target/classes`, because `target/generated-sources/annotations` keeps getting deleted on `mvn install`.
