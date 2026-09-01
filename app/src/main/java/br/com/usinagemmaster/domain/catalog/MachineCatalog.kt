package br.com.usinagemmaster.domain.catalog

import br.com.usinagemmaster.domain.model.EmployeeSpecialty
import br.com.usinagemmaster.domain.model.MachineDefinition
import br.com.usinagemmaster.domain.model.MachineType

object MachineCatalog {
    val all = listOf(
        MachineDefinition(MachineType.MECHANICAL_LATHE, "Torno Mecânico", 1_250_000, 9.0, 55, 5.0, 45_000, 10, EmployeeSpecialty.TURNER),
        MachineDefinition(MachineType.UNIVERSAL_MILL, "Fresadora Universal", 1_650_000, 7.0, 58, 7.5, 55_000, 12, EmployeeSpecialty.MILLER),
        MachineDefinition(MachineType.COLUMN_DRILL, "Furadeira de Coluna", 550_000, 12.0, 48, 2.2, 18_000, 6, EmployeeSpecialty.DRILL_OPERATOR),
        MachineDefinition(MachineType.CYLINDRICAL_GRINDER, "Retífica Cilíndrica", 1_950_000, 5.0, 78, 8.0, 70_000, 11, EmployeeSpecialty.GRINDER_OPERATOR),
        MachineDefinition(MachineType.WELDING_BENCH, "Bancada de Caldeiraria", 850_000, 8.0, 52, 4.0, 25_000, 9, EmployeeSpecialty.WELDER),
        MachineDefinition(MachineType.CNC_LATHE, "Torno CNC", 8_900_000, 28.0, 88, 14.0, 180_000, 13, EmployeeSpecialty.CNC_PROGRAMMER),
        MachineDefinition(MachineType.CNC_MACHINING_CENTER_3_AXIS, "Centro CNC 3 Eixos", 14_500_000, 36.0, 92, 20.0, 280_000, 18, EmployeeSpecialty.CNC_PROGRAMMER),
        MachineDefinition(MachineType.CNC_MACHINING_CENTER_5_AXIS, "Centro CNC 5 Eixos", 35_000_000, 55.0, 97, 32.0, 520_000, 22, EmployeeSpecialty.CNC_PROGRAMMER),
        MachineDefinition(MachineType.CNC_GRINDER, "Retífica CNC", 18_000_000, 24.0, 96, 21.0, 350_000, 16, EmployeeSpecialty.GRINDER_OPERATOR),
        MachineDefinition(MachineType.CNC_DRILL, "Furadeira CNC", 7_500_000, 32.0, 84, 12.0, 160_000, 12, EmployeeSpecialty.CNC_PROGRAMMER),
        MachineDefinition(MachineType.ROBOTIC_WELDING, "Solda Robotizada", 22_000_000, 45.0, 93, 25.0, 420_000, 20, EmployeeSpecialty.WELDER),
        MachineDefinition(MachineType.EDM, "Eletroerosão (EDM)", 19_500_000, 16.0, 99, 18.0, 360_000, 15, EmployeeSpecialty.CNC_PROGRAMMER),
        MachineDefinition(MachineType.LASER_CUTTER, "Corte a Laser", 28_000_000, 60.0, 95, 38.0, 500_000, 24, EmployeeSpecialty.CNC_PROGRAMMER),
        MachineDefinition(MachineType.PLASMA_CUTTER, "Corte a Plasma", 12_500_000, 50.0, 82, 28.0, 240_000, 20, EmployeeSpecialty.WELDER)
    )
    fun byType(type: String) = all.firstOrNull { it.type.name == type }
}
