# -*- mode: ruby -*-
# vi: set ft=ruby :

# VM configuration below is intended to imitate the Travis CI
# "container-based" infrastructure where the http4s builds run.
# https://docs.travis-ci.com/user/ci-environment/#Virtualization-environments

DEFAULT_CPU_COUNT = 2
DEFAULT_MEMORY = 4096
EXECUTION_CAP = 100       # reduce below 100 to simulate a sluggish Travis container
BOX = "ubuntu/precise64"  # 12.04 LTS

$script = <<SCRIPT
export DEBIAN_FRONTEND=noninteractive

# install Oracle JDK8 and SBT
echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections  # agree to EULA
echo debconf shared/accepted-oracle-license-v1-1 seen true   | sudo debconf-set-selections

sudo apt-add-repository -y ppa:webupd8team/java

echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823

sudo apt-get update
sudo apt-get install -y oracle-java8-installer sbt

# sanity check and force sbt to bootstrap
java -version
sbt about

# download most of the http4s dependencies
if [ -f "/vagrant/build.sbt" ]; then
    cd /vagrant
    sbt update
fi
SCRIPT

Vagrant.configure(2) do |config|
  config.vm.box = BOX

  if Vagrant.has_plugin?("vagrant-cachier")
      config.cache.scope = :box
  end

  config.vm.define "travis", primary: true do |travis|
      config.vm.hostname = "pseudo-travis"
      config.vm.provision "shell", inline: $script, privileged: false
      config.vm.post_up_message = "Done provisioning.  Use 'vagrant ssh' to login and 'cd /vagrant' to access http4s code."
  end

  # increase memory for Virtualbox
  config.vm.provider "virtualbox" do |vb|
      vb.cpus = DEFAULT_CPU_COUNT
      vb.memory = DEFAULT_MEMORY
      vb.customize ["modifyvm", :id, "--cpuexecutioncap", EXECUTION_CAP]
  end
end
