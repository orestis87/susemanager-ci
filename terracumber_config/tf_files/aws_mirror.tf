// Mandatory variables for terracumber
variable "URL_PREFIX" {
  type = "string"
  default = "https://ci.suse.de/view/Manager/view/Manager-4.1/job/SUSEManager-4.1-AWS"
}

// Not really used as this is for --runall parameter, and we run cucumber step by step
variable "CUCUMBER_COMMAND" {
  type = "string"
  default = "export PRODUCT='SUSE-Manager' && run-testsuite"
}

variable "CUCUMBER_GITREPO" {
  type = "string"
  default = "https://github.com/SUSE/spacewalk.git"
}

variable "CUCUMBER_BRANCH" {
  type = "string"
  default = "Manager-4.1"
}

variable "CUCUMBER_RESULTS" {
  type = "string"
  default = "/root/spacewalk/testsuite"
}

variable "MAIL_SUBJECT" {
  type = "string"
  default = "Results Manager4.1-Master-MU $status: $tests scenarios ($failures failed, $errors errors, $skipped skipped, $passed passed)"
}

variable "MAIL_TEMPLATE" {
  type = "string"
  default = "../mail_templates/mail-template-jenkins.txt"
}

variable "MAIL_SUBJECT_ENV_FAIL" {
  type = "string"
  default = "Results Uyuni-Master: Environment setup failed"
}

variable "MAIL_TEMPLATE_ENV_FAIL" {
  type = "string"
  default = "../mail_templates/mail-template-jenkins-env-fail.txt"
}

variable "MAIL_FROM" {
  type = "string"
  default = "mnoel@suse.de"
}

variable "MAIL_TO" {
  type = "string"
  default = "mnoel@suse.de"
}

// sumaform specific variables
variable "SCC_USER" {
  type = "string"
}

variable "SCC_PASSWORD" {
  type = "string"
}

variable "GIT_USER" {
  type = "string"
  default = null
  // Not needed for master, as it is public
}

variable "GIT_PASSWORD" {
  type = "string"
  default = null
  // Not needed for master, as it is public
}

variable "REGION" {
  type = "string"
  default = null
}

variable "AVAILABILITY_ZONE" {
  type = "string"
  default = null
}

variable "KEY_FILE" {
  type = "string"
  default = "/home/jenkins/.ssh/testing-suma.pem"
}

variable "KEY_NAME" {
  type = "string"
  default = "testing-suma"
}

variable "ACCESS_KEY" {
  type = "string"
  default = null
}

variable "SECRET_KEY" {
  type = "string"
  default = null
}

variable "ALLOWED_IPS" {
  type = list(string)
  default = []
}

variable "SERVER_REGISTRATION_CODE" {
  type = "string"
  default = null
}

variable "PROXY_REGISTRATION_CODE" {
  type = "string"
  default = null
}

variable "NAME_PREFIX" {
  type = string
  default = null
}

provider "aws" {
  region = var.REGION
  access_key = var.ACCESS_KEY
  secret_key = var.SECRET_KEY
}

module "base" {
  source = "./modules/base"

  images = [ "opensuse153o", "sles15sp2o" ]
  cc_username = var.SCC_USER
  cc_password = var.SCC_PASSWORD
  server_registration_code = var.SERVER_REGISTRATION_CODE
  proxy_registration_code = var.PROXY_REGISTRATION_CODE
  name_prefix = var.NAME_PREFIX
  //  mirror = "ip-172-16-1-50.eu-central-1.compute.internal"
  provider_settings = {
    availability_zone = var.AVAILABILITY_ZONE
    region = var.REGION
    ssh_allowed_ips = var.ALLOWED_IPS
    key_name = var.KEY_NAME
    key_file = var.KEY_FILE
  }
}


module "mirror" {
  source = "./modules/mirror"
  base_configuration = module.base.configuration
  disable_cron = true
  provider_settings = {
    public_instance = true
  }
}


output "bastion_public_name" {
  value = lookup(module.base.configuration, "bastion_host", null)
}

output "aws_mirrors_private_name" {
  value = module.mirror.configuration.hostnames
}

output "aws_mirrors_public_name" {
  value = module.mirror.configuration.public_names
}
