/**
 * Copyright (C) 2012 Red Hat, Inc. (jcasey@redhat.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import groovy.util.FileNameByRegexFinder

def pomFile = new File( basedir, 'pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )

def pom = new XmlSlurper().parse( pomFile )
System.out.println( "POM Version: ${pom.version.text()}" )

def repodir = new File(localRepositoryPath, "${pom.groupId.text().replace('.', '/')}/${pom.artifactId.text()}/${pom.version.text()}" )

def repopom = new File( repodir, "${pom.artifactId.text()}-${pom.version.text()}.pom" )
System.out.println( "Checking for installed pom: ${repopom.getAbsolutePath()}")
assert repopom.exists()

def bomrepodir = new File(localRepositoryPath, "${pom.groupId.text().replace('.', '/')}/bom/my-new-bom/${pom.version.text()}" )
def bomrepopom = new File( bomrepodir, "my-new-bom-${pom.version.text()}.pom" )
System.out.println( "Checking for installed bom: ${bomrepopom.getAbsolutePath()}")
assert bomrepopom.exists()
assert bomrepopom.text.contains ("dependencies in a dependencyManagement")

def bomdeploydir = new File(localRepositoryPath, "../local-deploy/${pom.groupId.text().replace('.', '/')}/bom/my-new-bom/${pom.version.text()}" )
System.out.println( "Checking for deployed bom: ${bomdeploydir.getAbsolutePath()}")

def bomdeploypomfiles = new FileNameByRegexFinder().getFileNames(bomdeploydir.getAbsolutePath(), 'my-new-bom.*.pom$')
bomdeploypomfiles.each { System.out.println( "Found " + it.toString()) ; assert (new File(it).text.contains ("dependencies in a dependencyManagement")) }

return true;
