rm  *.bin
rm  *.v
rm  *.vhd
rm src/main/scala/spinallab/soc/DrawCtrl.scala
rm src/main/scala/spinallab/soc/ApbGpio.scala
mv src/main/scala/spinallab/soc/ApbGpioStudent.scala src/main/scala/spinallab/soc/ApbGpio.scala
rm -rf .git
rm student.sh
echo "Do not forget to clean the toplevel"
