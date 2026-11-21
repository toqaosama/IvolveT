# Lab 28: Automated Host Discovery with Ansible Dynamic Inventory

## Objective

1. Create an AWS EC2 instance with the tag `server: ivolve`.
2. Set up an Ansible dynamic inventory that finds running EC2 instances with that tag.
3. List the hosts with `ansible-inventory`.
4. Run a playbook on the EC2.
5. Verify the configuration on the EC2.

## Structure

```text
lab-28/
├── ansible.cfg              # inventory = inventory/aws_ec2.yml, enable aws_ec2 plugin
├── inventory/aws_ec2.yml    # dynamic inventory (amazon.aws.aws_ec2)
├── create-ec2.sh            # AWS CLI: key pair + security group + tagged instance
├── playbook.yml             # installs Nginx + custom page on group server_ivolve
└── requirements.yml         # amazon.aws collection
```

## How discovery works

```yaml
plugin: amazon.aws.aws_ec2
filters:
  tag:server: ivolve               # only instances with this tag
  instance-state-name: running     # only running ones
compose:
  ansible_host: public_ip_address  # connect via public IP
keyed_groups:
  - key: tags.server
    prefix: server                 # → group "server_ivolve"
```

No IP is written anywhere: every run asks the AWS API which instances match.

## Steps

### 1. Prerequisites (control node - WSL)

```bash
cp -r /mnt/f/nti/ivolve/IvolveT/lab-28 ~/ && cd ~/lab-28
sudo apt install -y python3-boto3 python3-botocore
ansible-galaxy collection install -r requirements.yml
aws configure              # Access key, Secret key, region us-east-1
aws sts get-caller-identity
```

The IAM user needs at least `AmazonEC2FullAccess` (create) or `AmazonEC2ReadOnlyAccess` (inventory only)
and `ssm:GetParameter` for the AMI lookup.

### 2. Create the EC2 with tag `server=ivolve`

```bash
bash create-ec2.sh
```

Or from the console: **EC2 → Launch instance** → Ubuntu 24.04, `t3.micro`, key pair `ivolve-key`,
security group allowing 22 (your IP) and 80, **Tags: `server` = `ivolve`**.

### 3. List the discovered hosts

```bash
ansible-inventory --graph
ansible-inventory --list | head -50
```

Expected:

```text
@all:
  |--@ungrouped:
  |--@aws_ec2:
  |  |--ivolve-ec2
  |--@server_ivolve:
  |  |--ivolve-ec2
```

```bash
ansible server_ivolve -m ping
```

### 4. Run the playbook

```bash
ansible-playbook playbook.yml
```

### 5. Verify on the EC2

```bash
curl http://<EC2_PUBLIC_IP>
ansible server_ivolve -b -m command -a "systemctl is-active nginx"
```

Open `http://<EC2_PUBLIC_IP>` in the browser → "iVolve Lab 28" page.

### 6. Clean up (avoid AWS charges)

```bash
aws ec2 terminate-instances --instance-ids <INSTANCE_ID>
```

## Screenshots

- `screenshots/ec2-tag.png`
- `screenshots/inventory-graph.png`
- `screenshots/playbook-run.png`
- `screenshots/browser.png`
