package pe.upc.credito.infrastructure.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.entity.ProductEntity;
import pe.upc.credito.infrastructure.persistence.entity.StoreEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.ProductRepository;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;

/** Seeds synthetic Spanish-language examples for local development and browser tests. */
@Component
@Order(0)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = false)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final StoreRepository stores;
    private final UserRepository users;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(StoreRepository stores,
                          UserRepository users,
                          CustomerRepository customers,
                          ProductRepository products,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.demo-data.password}") String demoPassword) {
        this.stores = stores;
        this.users = users;
        this.customers = customers;
        this.products = products;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (stores.count() > 0) {
            log.info("Demo data skipped because the database already contains stores");
            return;
        }
        if (demoPassword.isBlank()) {
            throw new IllegalStateException("Set DEMO_ACCOUNT_PASSWORD before creating demo accounts");
        }

        List<StoreEntity> savedStores = stores.saveAll(List.of(
                store("Bodega Don Pepe", "20512345678", "Av. Los Olivos 450, Lima"),
                store("Minimarket La Esperanza", "20698765432", "Av. Grau 128, Trujillo")));
        StoreEntity firstStore = savedStores.get(0);
        StoreEntity secondStore = savedStores.get(1);

        String hash = passwordEncoder.encode(demoPassword);
        List<UserEntity> savedUsers = users.saveAll(List.of(
                user(null, "superadmin", hash, "Carlos", "Mendoza", "admin@sys.pe", "SUPER_ADMIN"),
                user(firstStore.getStoreId(), "pepe.admin", hash, "Jose", "Perez", "pepe@bodega.pe", "STORE_ADMIN"),
                user(secondStore.getStoreId(), "luis.admin", hash, "Luis", "Torres", "luis@esperanza.pe", "STORE_ADMIN"),
                user(firstStore.getStoreId(), "maria.quispe", hash, "Maria", "Quispe", "maria@mail.pe", "CUSTOMER"),
                user(firstStore.getStoreId(), "jose.ramirez", hash, "Jose", "Ramirez", "jose@mail.pe", "CUSTOMER"),
                user(firstStore.getStoreId(), "ana.torres", hash, "Ana", "Torres", "ana@mail.pe", "CUSTOMER"),
                user(secondStore.getStoreId(), "carlos.ruiz", hash, "Carlos", "Ruiz", "carlos@mail.pe", "CUSTOMER")));

        UserEntity maria = savedUsers.get(3);
        UserEntity jose = savedUsers.get(4);
        UserEntity ana = savedUsers.get(5);
        UserEntity carlos = savedUsers.get(6);

        customers.saveAll(List.of(
                customer(firstStore.getStoreId(), maria, "Calle Las Flores 210", "987654321",
                        "1500.00", 6, 25, 5, "EFFECTIVE", "0.1800000", "EFFECTIVE", "0.1200000"),
                customer(firstStore.getStoreId(), jose, "Av. Peru 1180", "912345678",
                        "800.00", 3, 20, 30, "NOMINAL", "0.2400000", "EFFECTIVE", "0.1500000"),
                customer(firstStore.getStoreId(), ana, "Jr. Puno 455", "998877665",
                        "2000.00", 12, 25, 5, "EFFECTIVE", "0.1500000", "EFFECTIVE", "0.1000000"),
                customer(secondStore.getStoreId(), carlos, "Av. Grau 128", "955443322",
                        "1200.00", 6, 25, 5, "EFFECTIVE", "0.2000000", "EFFECTIVE", "0.1200000")));

        products.saveAll(List.of(
                product(firstStore.getStoreId(), "Saco de Arroz Extra 50kg",
                        "Arroz superior anejo en saco de 50 kg", "Costeno",
                        "Distribuidora Norte SAC", "BAG", "160.00", "175.00", 25, 5),
                product(firstStore.getStoreId(), "Aceite Vegetal Primor 1L",
                        "Aceite vegetal comestible de un litro", "Primor",
                        "Alicorp", "UNIT", "8.50", "9.50", 48, 12),
                product(firstStore.getStoreId(), "Azucar Rubia Cartavio 5kg",
                        "Azucar rubia en bolsa de cinco kilos", "Cartavio",
                        "Cartavio S.A.A.", "PACK", "22.00", "24.50", 30, 10),
                product(firstStore.getStoreId(), "Leche Gloria Tarro 400g",
                        "Leche evaporada entera en tarro", "Gloria",
                        "Gloria S.A.", "UNIT", "4.50", "5.20", 60, 15),
                product(firstStore.getStoreId(), "Fideos Don Vittorio 500g",
                        "Fideos tallarin en paquete de medio kilo", "Don Vittorio",
                        "Molitalia", "PACK", "3.20", "3.80", 40, 10),
                product(firstStore.getStoreId(), "Atun Florida 170g",
                        "Atun en aceite en lata de 170 gramos", "Florida",
                        "Conservas Florida", "UNIT", "5.50", "6.20", 35, 8),
                product(firstStore.getStoreId(), "Detergente Bolivar 780g",
                        "Detergente en polvo para ropa", "Bolivar",
                        "Alicorp", "PACK", "11.90", "13.50", 20, 5),
                product(firstStore.getStoreId(), "Gaseosa Inca Kola 1.5L",
                        "Bebida gaseosa de un litro y medio", "Inca Kola",
                        "Coca-Cola Peru", "UNIT", "6.50", "7.50", 24, 6),
                product(secondStore.getStoreId(), "Arroz Costeno 5kg",
                        "Arroz extra en bolsa de cinco kilos", "Costeno",
                        "Distribuidora Norte SAC", "PACK", "21.00", "23.00", 18, 5),
                product(secondStore.getStoreId(), "Aceite Cocinero 900ml",
                        "Aceite vegetal de 900 mililitros", "Cocinero",
                        "Alicorp", "UNIT", "8.90", "9.90", 22, 6),
                product(secondStore.getStoreId(), "Leche Laive 1L",
                        "Leche fresca entera de un litro", "Laive",
                        "Laive S.A.", "UNIT", "4.80", "5.50", 15, 5)));

        log.info("Demo data created: 2 stores, 7 users, 4 customers and 11 products");
    }

    private StoreEntity store(String businessName, String taxId, String address) {
        StoreEntity store = new StoreEntity();
        store.setBusinessName(businessName);
        store.setTaxId(taxId);
        store.setAddress(address);
        store.setStatus("ACTIVE");
        return store;
    }

    private UserEntity user(Integer storeId, String username, String passwordHash,
                            String firstName, String lastName, String email, String userType) {
        UserEntity user = new UserEntity();
        user.setStoreId(storeId);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setUserType(userType);
        user.setStatus("ACTIVE");
        return user;
    }

    private CustomerEntity customer(Integer storeId, UserEntity user, String homeAddress,
                                    String phoneNumber, String creditLimit, int maxCreditMonths,
                                    int cutoffDay, int paymentDueDay, String compensatoryType,
                                    String compensatoryValue, String penaltyType, String penaltyValue) {
        CustomerEntity customer = new CustomerEntity();
        customer.setStoreId(storeId);
        customer.setUserId(user.getUserId());
        customer.setHomeAddress(homeAddress);
        customer.setPhoneNumber(phoneNumber);
        customer.setCreditLimit(new BigDecimal(creditLimit));
        customer.setMaxCreditMonths(maxCreditMonths);
        customer.setCutoffDay(cutoffDay);
        customer.setPaymentDueDay(paymentDueDay);
        customer.setCompensatoryRateType(compensatoryType);
        customer.setCompensatoryRatePeriodDays(360);
        customer.setCompensatoryCapitalizationDays("NOMINAL".equals(compensatoryType) ? 1 : null);
        customer.setCompensatoryRateValue(new BigDecimal(compensatoryValue));
        customer.setPenaltyRateType(penaltyType);
        customer.setPenaltyRatePeriodDays(360);
        customer.setPenaltyCapitalizationDays("NOMINAL".equals(penaltyType) ? 1 : null);
        customer.setPenaltyRateValue(new BigDecimal(penaltyValue));
        customer.setCreditCurrency("PEN");
        customer.setFavourBalance(BigDecimal.ZERO.setScale(2));
        return customer;
    }

    private ProductEntity product(Integer storeId, String name, String description, String brand,
                                  String supplier, String unitOfMeasure, String cashPrice,
                                  String creditPrice, int currentStock, int minStock) {
        ProductEntity product = new ProductEntity();
        product.setStoreId(storeId);
        product.setName(name);
        product.setDescription(description);
        product.setBrand(brand);
        product.setSupplier(supplier);
        product.setUnitOfMeasure(unitOfMeasure);
        product.setCashPrice(new BigDecimal(cashPrice));
        product.setCreditListPrice(new BigDecimal(creditPrice));
        product.setAllowsInstallments(true);
        product.setCurrentStock(currentStock);
        product.setMinStock(minStock);
        product.setStatus("AVAILABLE");
        return product;
    }
}
