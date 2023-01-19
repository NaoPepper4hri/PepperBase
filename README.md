# PepperBase
Basic application running an animation in Pepper using qiSDK.

This app intends to be a starting point for all applications that need the use of an android version of the Pepper robot.

In the future, the code will include steps to create the most common actions with small changes to the code.

# Troubleshooting emulator

Unfortunately, the emulator for Pepper does not work in most systems.
If you are using a linux based distribution (e.g Ubuntu), there is a easy workaround to this:

1. Install qemu and kvm. These commands might differ depending on your distribution, but for Ubuntu the commands would be:
```
sudo apt install qemu-kvm
sudo adduser yourusername kvm
```

2. Then, go to the robot sdk libraries,
```
cd /home/$USER/.local/share/Softbank Robotics/RobotSDK/API 7/tools/lib
```

3. Back up the old library,
```
mv libz.so.1 libz.so.1.bak
```

4. And substitute the libz library with a link to the one installed in your system.
```
ln -s /usr/lib/x86_64-linux-gnu/libz.so libz.so.1
```

5. Restart Android Studio and start the Emulator.
