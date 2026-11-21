#!/usr/bin/env bash
# Create an Ubuntu EC2 instance tagged server=ivolve (AWS CLI v2, configured with `aws configure`).
set -euo pipefail

REGION="${REGION:-us-east-1}"
KEY_NAME="${KEY_NAME:-ivolve-key}"
SG_NAME="${SG_NAME:-ivolve-sg}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.micro}"

# Latest Ubuntu 24.04 LTS AMI from Canonical's public SSM parameter
AMI_ID=$(aws ssm get-parameter --region "$REGION" \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query Parameter.Value --output text)

# Key pair (saved to ~/.ssh/ivolve-key.pem)
if ! aws ec2 describe-key-pairs --region "$REGION" --key-names "$KEY_NAME" >/dev/null 2>&1; then
  aws ec2 create-key-pair --region "$REGION" --key-name "$KEY_NAME" \
    --query KeyMaterial --output text > ~/.ssh/"$KEY_NAME".pem
  chmod 400 ~/.ssh/"$KEY_NAME".pem
fi

# Security group: SSH from my IP, HTTP from anywhere
VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text)
SG_ID=$(aws ec2 describe-security-groups --region "$REGION" \
  --filters Name=group-name,Values="$SG_NAME" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text)
if [ "$SG_ID" = "None" ]; then
  SG_ID=$(aws ec2 create-security-group --region "$REGION" --group-name "$SG_NAME" \
    --description "iVolve lab 28" --vpc-id "$VPC_ID" --query GroupId --output text)
  MY_IP=$(curl -s https://checkip.amazonaws.com)
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 22 --cidr "$MY_IP/32"
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 80 --cidr 0.0.0.0/0
fi

INSTANCE_ID=$(aws ec2 run-instances --region "$REGION" \
  --image-id "$AMI_ID" --instance-type "$INSTANCE_TYPE" \
  --key-name "$KEY_NAME" --security-group-ids "$SG_ID" \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=ivolve-ec2},{Key=server,Value=ivolve}]' \
  --query 'Instances[0].InstanceId' --output text)

echo "Waiting for $INSTANCE_ID to run..."
aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"

aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].[InstanceId,PublicIpAddress,Tags]' --output json
