# Lab 24: Initial Ansible Configuration and Ad-Hoc Execution

## Objective

1. Install and configure Ansible on the control node.
2. Generate a new SSH key on the control node.
3. Copy the public key to the managed node with `ssh-copy-id`.
4. Create an inventory for the managed node.
5. Install Docker on the managed node with an ad-hoc command.

## Environment

| Role | Machine |
|---|---|
| Control node | WSL Ubuntu on Windows |
| Managed node | Ubuntu server (AWS EC2 `t3.micro`/`t2.micro`, user `ubuntu`) |

> **Note (WSL):** Ansible ignores `ansible.cfg` inside a world-writable folder such as `/mnt/f/...`.
> Copy the lab into the Linux home first:
> `cp -r /mnt/f/nti/ivolve/IvolveT/lab-24 ~/ && cd ~/lab-24`

> **How it was run:** the control node is a Linux container (`ansible-control`, Python 3.12 +
> `pip install ansible-core`) because `sudo` on the WSL machine needs a password. Any Linux
> control node works the same way. Managed node: AWS EC2 Ubuntu 26.04 (`75.101.215.238`).

## Files

```text
lab-24/
├── ansible.cfg      # inventory, remote user, key, no host key checking
└── inventory.ini    # [managed] group with node1
```

## Steps

### 1. Install Ansible on the control node

```bash
sudo apt update
sudo apt install -y software-properties-common
sudo add-apt-repository --yes --update ppa:ansible/ansible
sudo apt install -y ansible
ansible --version
```

Alternative without root (what was used here): `pip install ansible-core`

![ansible --version](screenshots/ansible-version.png)

### 2. Generate a new SSH key

```bash
ssh-keygen -t ed25519 -f ~/.ssh/ansible_key -C "ansible-control" -N ""
ls -l ~/.ssh/ansible_key*
```

![ssh-keygen](screenshots/ssh-keygen.png)

### 3. Copy the public key to the managed node

```bash
# EC2 only allows the .pem key at first, so pass it to ssh-copy-id with -o
ssh-copy-id -f -i ~/.ssh/ansible_key.pub \
  -o "IdentityFile ~/.ssh/<your-ec2-key>.pem" ubuntu@<MANAGED_NODE_IP>

# now login works with the new key
ssh -i ~/.ssh/ansible_key ubuntu@<MANAGED_NODE_IP> hostname
```

`-f` is needed here: otherwise ssh-copy-id logs in with the .pem, thinks the key is already there and skips it.

![ssh-copy-id](screenshots/ssh-copy-id.png)

(For a normal VM with password login: `ssh-copy-id -i ~/.ssh/ansible_key.pub user@IP`.)

### 4. Create the inventory

Edit `inventory.ini` and put the managed node IP:

```ini
[managed]
node1 ansible_host=<MANAGED_NODE_IP> ansible_user=ubuntu
```

Test the connection:

```bash
ansible-inventory --graph
ansible managed -m ping
```

Expected: `node1 | SUCCESS => { "ping": "pong" }`

![Inventory graph and ping](screenshots/inventory-ping.png)

### 5. Ad-hoc command to install Docker

```bash
ansible managed -b -m apt -a "name=docker.io state=present update_cache=yes"
ansible managed -b -m service -a "name=docker state=started enabled=yes"
```

Verify:

```bash
ansible managed -b -m command -a "docker --version"
ansible managed -b -m command -a "systemctl is-active docker"
```

`CHANGED` on the first run = Docker was installed by the ad-hoc command:

![Ad-hoc Docker install](screenshots/adhoc-docker.png)

## Screenshots

| Step | Screenshot |
|---|---|
| Ansible installed | [ansible-version.png](screenshots/ansible-version.png) |
| New SSH key | [ssh-keygen.png](screenshots/ssh-keygen.png) |
| ssh-copy-id | [ssh-copy-id.png](screenshots/ssh-copy-id.png) |
| Inventory + ping | [inventory-ping.png](screenshots/inventory-ping.png) |
| Ad-hoc Docker install | [adhoc-docker.png](screenshots/adhoc-docker.png) |
